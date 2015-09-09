package edu.berkeley.nlp.coref

import java.io._
import java.util.zip.GZIPInputStream

import edu.berkeley.nlp.coref.config.{CorefSystemConfiguration, InferenceType}
import edu.berkeley.nlp.coref.exception.{ThesaurusFeaturesMissingException, LoadModelException}
import edu.berkeley.nlp.coref.io.ConllDocWriter
import edu.berkeley.nlp.futile.fig.basic.{IOUtils, Indexer, SysInfoUtils}
import edu.berkeley.nlp.futile.util.Logger
import org.jobimtext.coref.berkeley.{ThesaurusFeature, ThesaurusLoader}

import scala.collection.mutable.ListBuffer

class CorefSystem(val config: CorefSystemConfiguration) {

  def checkFileReachableForRead(filename: String, msg: String) {
    if (Option(filename).getOrElse("").isEmpty) throw new RuntimeException("Undefined " + msg + "; must be defined " +
      "for the mode you're running in")

    if (!new File(filename).exists()) {
      throw new RuntimeException(msg + " file/directory doesn't exist for read: " + filename)
    }
  }

  def checkFileReachableForWrite(filename: String, msg: String) {
    if (Option(filename).getOrElse("").isEmpty) throw new RuntimeException("Undefined " + msg + "; must be defined " +
      "for the mode you're running in")


    val file = new File(filename)
    if (file.exists()) {
      if (!file.canWrite) {
        throw new RuntimeException(msg + " file/directory couldn't be opened for write: " + file)
      }
    } else {
      // just try it out
      try {
        file.createNewFile()
      } catch {
        case e: Exception => throw new RuntimeException(msg + " file/directory couldn't be opened for write: " +
          file, e)
      }
    }
  }

  def convertToCorefDocs(docs: Seq[BaseDoc], mentionPropertyComputer: MentionPropertyComputer): Seq[CorefDoc] = {
    val assembler = CorefDocAssembler(config)
    val corefDocs = docs.map(doc => assembler.createCorefDoc(doc, mentionPropertyComputer))
    CorefDoc.checkGoldMentionRecall(corefDocs)
    corefDocs
  }

  def createModelContainer(scorer: PairwiseScorer) = {
    val thesaurusFeatures = if (scorer.featurizer.mentionPropertyComputer.thesauri != null) {
      scorer.featurizer.mentionPropertyComputer.thesauri.featuresToUse
    } else {
      Array[ThesaurusFeature]()
    }
    new ModelContainer(scorer, thesaurusFeatures)
  }

  def saveModelFile(scorer: PairwiseScorer, modelPath: String) {
    val container = createModelContainer(scorer)

    // non serializable MentionPropertyComputer (also, don't store database connections w/ passwords!)
    scorer.featurizer.mentionPropertyComputer = null
    try {
      val fileOut = new FileOutputStream(modelPath)
      val out = new ObjectOutputStream(fileOut)
      out.writeObject(container)
      Logger.logss("Model written to " + modelPath)
      out.close()
      fileOut.close();
    } catch {
      case e: Exception => throw new RuntimeException(e);
    }
  }

  def preprocessDocsClusterInfo(allDocGraphs: Seq[DocumentGraph]) {
    // Store oracle information
    if (config.cheat) {
      val rng = new java.util.Random(0)
      for (i <- 0 until config.numCheatingProperties) {
        allDocGraphs.map(_.computeAndStoreCheatingPosteriors(config.cheatingDomainSize, rng))
      }
    }
    // Store phi features
    if (config.phi) {
      val useNum = config.phiClusterFeatures.contains("numb")
      val useGender = config.phiClusterFeatures.contains("gend")
      val useNert = config.phiClusterFeatures.contains("nert")
      allDocGraphs.map(_.computeAndStorePhiPosteriors(useNum, useGender, useNert))
    }
  }


  def createPropertyComputer() = CorefSystem.createPropertyComputer(config)

  def createBasicInferencer(featureIndexer: Indexer[String], propertyComputer: MentionPropertyComputer,
                            lexicalCounts: LexicalCountsBundle) = {
    new PairwiseIndexingFeaturizerJoint(featureIndexer, config.pairwiseFeats, config.conjType, config.dtConjType,
      config.dtRemoveIncompatibleTermsK, config.discretizeIntervalFactor, config.chimergeIntervalsFile,
      lexicalCounts, propertyComputer)
  }

  /*def runTrainEvaluate(trainDocs: => Seq[BaseDoc], testDocs: => Seq[BaseDoc], modelPath: String,
                       conllEvalScriptPath: String) {
    checkFileReachableForRead(conllEvalScriptPath, "conllEvalScriptPath")

    val scorer = runTrain(trainDocs)

    val propertyComputer = scorer.featurizer.mentionPropertyComputer

    if (!modelPath.isEmpty) {
      saveModelFile(scorer, modelPath)
    }

    // restore destroyed property computer
    scorer.featurizer.mentionPropertyComputer = propertyComputer

    runEvaluate(testDocs, createModelContainer(scorer), conllEvalScriptPath)
  }*/

/*  def runTrainPredict(trainDocs: => Seq[BaseDoc], predictDocs: => Seq[BaseDoc], modelPath: String,
                      outPath: String, doConllPostprocessing: Boolean) {
    checkFileReachableForWrite(outPath, "outputPath")
    val scorer = runTrain(trainDocs)
    if (!modelPath.isEmpty) {
      saveModelFile(scorer, modelPath)
    }
    runPredict(predictDocs, createModelContainer(scorer), outPath, doConllPostprocessing)
  }*/

  def runTrain(trainDocs: => Seq[BaseDoc], modelPath: String): PairwiseScorer = {
    checkFileReachableForWrite(modelPath, "modelPath")
    val scorer = runTrain(trainDocs)
    saveModelFile(scorer, modelPath)

    scorer
  }

  def runTrain(trainDocs: => Seq[BaseDoc]): PairwiseScorer = {
    val propertyComputer = createPropertyComputer()

    val trainCorefDocs = convertToCorefDocs(trainDocs, propertyComputer)

    require(trainCorefDocs.nonEmpty, "no training documents loaded, aborting");

    val trainDocGraphs = trainCorefDocs.map(new DocumentGraph(_, true))
    val lexicalCounts = LexicalCountsBundle.countLexicalItems(trainCorefDocs, config.lexicalFeatCutoff)

    Logger.logss("PRUNING BY DISTANCE")
    DocumentGraph.pruneEdgesAll(trainDocGraphs, new PruningStrategy(config.pruningStrategy), null)

    val featureIndexer = new Indexer[String]()
    featureIndexer.getIndex(PairwiseIndexingFeaturizerJoint.UnkFeatName)
    val basicFeaturizer = createBasicInferencer(featureIndexer, propertyComputer, lexicalCounts)

    val featurizerTrainer = new CorefFeaturizerTrainer(config)
    featurizerTrainer.featurizeBasic(trainDocGraphs, basicFeaturizer)
    basicFeaturizer.printFeatureTemplateCounts()

    if (propertyComputer.thesauri != null) {
      propertyComputer.thesauri.closeConnections()
    }

    val basicInferencer = new DocumentInferencerBasic(config)
    val lossFcnObjFirstPass = PairwiseLossFunctions(config.lossFcn)
    val firstPassWeights = featurizerTrainer.train(trainDocGraphs,
      basicFeaturizer,
      config.eta,
      config.reg,
      lossFcnObjFirstPass,
      config.numItrs,
      basicInferencer)
    new PairwiseScorer(basicFeaturizer, firstPassWeights)
  }

  def runEvaluate(testDocs: => Seq[BaseDoc], modelPath: String, conllScorerPath: String) {
    runEvaluate(testDocs, CorefSystem.loadModelFile(modelPath), conllScorerPath)
  }

  def runEvaluate(testDocs: => Seq[BaseDoc], modelContainer: ModelContainer, conllEvalScriptPath: String) {
    val propertyComputer = createPropertyComputer()

    val scorer = modelContainer.scorer

    CorefSystem.ensureThesauriPresent(modelContainer, propertyComputer)

    // property computer is deleted during saving/should not be reused due to database connections
    scorer.featurizer.mentionPropertyComputer = propertyComputer
    val testCorefDocs = convertToCorefDocs(testDocs, propertyComputer)
    val testDocGraphs = testCorefDocs.map(new DocumentGraph(_, false))
    DocumentGraph.pruneEdgesAll(testDocGraphs, new PruningStrategy(config.pruningStrategy), null)
    new CorefFeaturizerTrainer(config).featurizeBasic(testDocGraphs, scorer.featurizer); // test docs don't add new
    // features

    if (propertyComputer.thesauri != null) {
      propertyComputer.thesauri.closeConnections()
    }

    Logger.startTrack("Decoding dev")
    val basicInferencer = new DocumentInferencerBasic(config)
    Logger.logss(CorefEvaluator.evaluateAndRender(testDocGraphs, basicInferencer, scorer, conllEvalScriptPath,
      "DEV: ", config))

    Logger.endTrack()
  }

  def runPredict(predictDocs: => Seq[BaseDoc], modelPath: String, outPath: String, doConllPostprocessing: Boolean) {
    runPredict(predictDocs, CorefSystem.loadModelFile(modelPath), outPath, doConllPostprocessing)
  }

  def runPredict(predictDocs: => Seq[BaseDoc], model: ModelContainer, outPath: String,
                 doConllPostprocessing: Boolean) {
    checkFileReachableForWrite(outPath, "outputPath")
    val propertyComputer = createPropertyComputer()

    CorefSystem.ensureThesauriPresent(model, propertyComputer)
    val scorer = model.scorer
    scorer.featurizer.mentionPropertyComputer = propertyComputer

    val predictCorefDocs = convertToCorefDocs(predictDocs, propertyComputer)
    val predictDocGraphs = predictCorefDocs.map(new DocumentGraph(_, false))
    DocumentGraph.pruneEdgesAll(predictDocGraphs, new PruningStrategy(config.pruningStrategy), scorer)
    new CorefFeaturizerTrainer(config).featurizeBasic(predictDocGraphs, scorer.featurizer) // dev docs already know
    // they are
    // dev docs so they don't add features
    Logger.startTrack("Decoding dev")
    val basicInferencer = new DocumentInferencerBasic(config)
    val (allPredBackptrs, allPredClusterings) = basicInferencer.viterbiDecodeAllFormClusterings(predictDocGraphs,
      scorer)
    val writer = IOUtils.openOutHard(outPath + File.separator + "prediction.result")
    for (i <- 0 until predictDocGraphs.size) {
      val outputClustering = new OrderedClusteringBound(predictDocGraphs(i).getMentions(), allPredClusterings(i))
      ConllDocWriter.writeDoc(writer, predictDocGraphs(i).corefDoc.rawDoc, if (doConllPostprocessing) outputClustering
        .postprocessForConll()
      else outputClustering)

    }
    writer.close()
  }

  def runNewOnlyTwoPass(trainDocs: => Seq[BaseDoc], testDocs: => Seq[BaseDoc], conllEvalScriptPath: String) {
    checkFileReachableForRead(conllEvalScriptPath, "conllEvalScriptPath")
    val propertyComputer = createPropertyComputer()

    val trainCorefDocs = convertToCorefDocs(trainDocs, createPropertyComputer())
    val testCorefDocs = convertToCorefDocs(testDocs, createPropertyComputer())
    val trainDocGraphs = trainCorefDocs.map(new DocumentGraph(_, true))
    val testDocGraphs = testCorefDocs.map(new DocumentGraph(_, false))
    val lexicalCounts = LexicalCountsBundle.countLexicalItems(trainCorefDocs, config.lexicalFeatCutoff)

    preprocessDocsClusterInfo(trainDocGraphs ++ testDocGraphs)

    val featurizerTrainer = new CorefFeaturizerTrainer(config)
    val lossFcnObjFirstPass = PairwiseLossFunctions(config.lossFcn)
    Logger.logss("PRUNING BY DISTANCE")
    DocumentGraph.pruneEdgesAll(trainDocGraphs, new PruningStrategy(config.pruningStrategy), null)
    DocumentGraph.pruneEdgesAll(testDocGraphs, new PruningStrategy(config.pruningStrategy), null)

    val featureIndexer = new Indexer[String]()
    featureIndexer.getIndex(PairwiseIndexingFeaturizerJoint.UnkFeatName)
    val basicFeaturizer = createBasicInferencer(featureIndexer, propertyComputer, lexicalCounts)

    featurizerTrainer.featurizeBasic(trainDocGraphs, basicFeaturizer)
    featurizerTrainer.featurizeBasic(testDocGraphs, basicFeaturizer); // dev docs already know they are dev docs so
    // they don't add features
    basicFeaturizer.printFeatureTemplateCounts()

    val basicInferencer = if (config.inferenceType != InferenceType.BINARY) {
      new DocumentInferencerBasic(config)
    } else {
      new DocumentInferencerBinary(config.binaryLogThreshold * (if (config.binaryNegateThreshold) -1.0 else 1.0),
        config.binaryClusterType,
        config.binaryNegativeClassWeight)
    }
    val firstPassWeights = featurizerTrainer.train(trainDocGraphs,
      basicFeaturizer,
      config.eta,
      config.reg,
      lossFcnObjFirstPass,
      config.numItrs,
      basicInferencer)
    val firstPassPairwiseScorer = new PairwiseScorer(basicFeaturizer, firstPassWeights)
    Logger.startTrack("Decoding dev")
    Logger.logss(CorefEvaluator.evaluateAndRender(testDocGraphs, basicInferencer, firstPassPairwiseScorer,
      conllEvalScriptPath, "DEV: ", config))

    Logger.endTrack()
    if (config.inferenceType == InferenceType.BINARY) {
      return
    }

    Logger.logss("PRUNING WITH ACTUAL STRATEGY")
    Logger.logss("Memory before pruning: " + SysInfoUtils.getUsedMemoryStr)
    DocumentGraph.pruneEdgesAll(trainDocGraphs, new PruningStrategy(config.pruningStrategySecondPass),
      firstPassPairwiseScorer)

    DocumentGraph.pruneEdgesAll(testDocGraphs, new PruningStrategy(config.pruningStrategySecondPass),
      firstPassPairwiseScorer)

    Logger.logss("Memory after pruning: " + SysInfoUtils.getUsedMemoryStr)

    val lossFcnObjSecondPass = PairwiseLossFunctions(config.lossFcnSecondPass)

    // Learn the advanced model
    Logger.logss("Refeaturizing for second pass")
    val secondPassFeatureIndexer = new Indexer[String]()
    secondPassFeatureIndexer.getIndex(PairwiseIndexingFeaturizerJoint.UnkFeatName)
    val secondPassBasicFeaturizer = new PairwiseIndexingFeaturizerJoint(secondPassFeatureIndexer,
      config.pairwiseFeatsSecondPass, config.conjTypeSecondPass, config.dtConjType,
      config.dtRemoveIncompatibleTermsK, config.discretizeIntervalFactor, config.chimergeIntervalsFile,
      lexicalCounts, propertyComputer)

    // Explicitly clear the caches and refeaturize the documents
    trainDocGraphs.foreach(_.cacheEmpty = true)
    featurizerTrainer.featurizeBasic(trainDocGraphs, secondPassBasicFeaturizer)
    testDocGraphs.foreach(_.cacheEmpty = true)
    featurizerTrainer.featurizeBasic(testDocGraphs, secondPassBasicFeaturizer); // dev docs already know they are dev
    // docs so they don't add features
    Logger.logss(secondPassFeatureIndexer.size() + " features after refeaturization")
    val (secondPassFeaturizer, secondPassInferencer) = if (config.inferenceType == InferenceType.LOOPY) {
      val numFeatsBeforeLoopyPass = secondPassBasicFeaturizer.getIndexer.size()
      featurizerTrainer.featurizeLoopyAddToIndexer(trainDocGraphs, secondPassBasicFeaturizer)
      Logger.logss("Features before loopy pass: " + numFeatsBeforeLoopyPass + ", after: " + secondPassBasicFeaturizer
        .getIndexer.size())

      val inferencer = new DocumentInferencerLoopy(config)
      (secondPassBasicFeaturizer, inferencer)
    } else if (config.inferenceType == InferenceType.RAHMAN) {
      val numFeatsBeforeLoopyPass = secondPassBasicFeaturizer.getIndexer.size()
      val entityFeaturizer = new EntityFeaturizer(config.clusterFeats)
      featurizerTrainer.featurizeRahmanAddToIndexer(trainDocGraphs, secondPassBasicFeaturizer, entityFeaturizer)
      Logger.logss("Features before Rahman pass: " + numFeatsBeforeLoopyPass + ", " +
        "after: " + secondPassBasicFeaturizer.getIndexer.size())

      val inferencer = new DocumentInferencerRahman(entityFeaturizer, secondPassBasicFeaturizer.getIndexer,
        config.rahmanTrainType)

      (secondPassBasicFeaturizer, inferencer)
    } else {
      (secondPassBasicFeaturizer, new DocumentInferencerBasic(config))
    }

    val secondPassWeights = featurizerTrainer.train(trainDocGraphs,
      secondPassFeaturizer,
      config.eta,
      config.reg,
      lossFcnObjSecondPass,
      config.numItrsSecondPass,
      secondPassInferencer)

    val secondPassPairwiseScorer = new PairwiseScorer(secondPassFeaturizer, secondPassWeights)
    Logger.startTrack("Decoding dev")
    Logger.logss(CorefEvaluator.evaluateAndRender(testDocGraphs, secondPassInferencer, secondPassPairwiseScorer,
      conllEvalScriptPath, "DEV: ", config))

    Logger.endTrack()

    if (propertyComputer.thesauri != null) propertyComputer.thesauri.closeConnections()
  }

  /**
   * Outputs the weights of a model to STDOUT.
   *
   * @param modelPath path to a pretrained model
   */
  def outputWeights(modelPath: String) {
    val model = CorefSystem.loadModelFile(modelPath)
    val weightPairs = new ListBuffer[(String, Double)]

    for (i <- 0 until model.scorer.numWeights) {
      val feature = model.scorer.featurizer.getIndexer().getObject(i)
      val weight = model.scorer.weights(i)
      weightPairs += ((feature, weight))
    }

    weightPairs.sorted(Ordering.by[(String, Double), Double](-_._2)).foreach(println)
  }
}

object CorefSystem {
  @throws[LoadModelException]("If the model could not be loaded")
  def loadModelFile(modelPath: String): ModelContainer = loadModelFile(new File(modelPath))

  @throws[LoadModelException]("If the model could not be loaded")
  def loadModelFile(modelFile: File): ModelContainer = {
    Logger.logss("Producing model from " + modelFile.getAbsolutePath)
    val stream = if (modelFile.getName.endsWith(".gz")) new GZIPInputStream(new FileInputStream(modelFile), 65536)
    else new FileInputStream(modelFile)
    val container: ModelContainer = loadModelFile(stream)
    Logger.logss("Finished reading model")
    container
  }

  @throws[LoadModelException]("If the model could not be loaded")
  def loadModelFile(modelStream: InputStream): ModelContainer = {
    var container: ModelContainer = null
    try {
      val in = new ObjectInputStream(new BufferedInputStream(modelStream))
      container = in.readObject().asInstanceOf[ModelContainer]
      in.close()
    } catch {
      case e: IOException => throw new LoadModelException("Could not load model file", e);
    }
    container
  }

  @throws[ThesaurusFeaturesMissingException]("If the thesaurus features do not match")
  def ensureThesauriPresent(modelContainer: ModelContainer, propertyComputer: MentionPropertyComputer) {
    if (modelContainer.thesaurusFeatures.nonEmpty) {
      if (propertyComputer.thesauri == null) {
        throw new IllegalStateException("Loaded model requires a thesaurus configuration with the features " +
          modelContainer.thesaurusFeatures)

      }

      val missingFeatures = modelContainer.thesaurusFeatures.filterNot(propertyComputer.thesauri.featuresToUse.contains
        (_))

      if (missingFeatures.nonEmpty) throw new ThesaurusFeaturesMissingException(missingFeatures)
    }
  }

  def createPropertyComputer(config: CorefSystemConfiguration): MentionPropertyComputer = {
    val computer = new MentionPropertyComputer(config.languagePack)
    computer.ngComputer = config.numberGenderComputer
    computer.thesauri = config.thesaurusCollection.orNull
    computer.config = config
    computer
  }
}