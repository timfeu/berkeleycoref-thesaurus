package org.jobimtext.coref.berkeley.uima

import de.tudarmstadt.ukp.dkpro.core.api.coref.`type`.{CoreferenceChain, CoreferenceLink}
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.`type`.Token
import edu.berkeley.nlp.coref._
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.exception.ThesaurusFeaturesMissingException
import edu.berkeley.nlp.coref.lang.{CorefLanguagePack, Language, LanguagePackFactory}
import org.apache.uima.analysis_engine.AnalysisEngineProcessException
import org.apache.uima.fit.component.JCasAnnotator_ImplBase
import org.apache.uima.jcas.JCas
import org.apache.uima.resource.ResourceInitializationException
import org.apache.uima.util.{Level, Logger}

/**
 * Actual implementation of the [[CoreferenceAnalysisEngine]], which was created as a
 * Java class to better interoperate with UIMA (checked exceptions, etc.).
 *
 * @param config the CorefSystem configuration to use during processing. The number gender computer
 *               and language pack will be overridden for each call to process
 * @param modelProvider provider producing the model
 * @param numberGenderComputerProvider provider producing the number gender computer
 * @param logger used to log various kinds of messages during processing
 *
 * @author Tim Feuerbach
 */
protected[uima] class CoreferenceAnalysisEngineImpl(val config: CorefSystemConfiguration,
                                                    val modelProvider: CasConfigurableProviderBase[ModelContainer],
                                                    val numberGenderComputerProvider: CasConfigurableProviderBase[NumberGenderComputer],
                                                    val logger: Logger) {

  // we are cloning the configuration file since we set the language pack depending on the CAS language
  protected val configClone = config.clone()

  protected val docConverter = new CASToBaseDocConverter(configClone, logger)

  protected val assembler = new CorefDocAssembler(configClone)

  protected val featurizerTrainer = new CorefFeaturizerTrainer(configClone)

  protected val basicInferencer = new DocumentInferencerBasic(configClone)

  def createLanguagePackForISOLanguage(language: String): CorefLanguagePack = language.take(2) match {
    case "en" => LanguagePackFactory.getLanguagePack(Language.ENGLISH)
    case "ar" => LanguagePackFactory.getLanguagePack(Language.ARABIC)
    case "zh" => LanguagePackFactory.getLanguagePack(Language.CHINESE)
    case _ => logger.log(Level.WARNING, s"Unknown language $language, falling back to {$config.getLanguagePack}")
      config.getLanguagePack
  }

  // redirect Berkeley logger calls to the UIMA logger
  edu.berkeley.nlp.futile.util.Logger.setGlobalLogger(new UimaFutileLogger(logger))

  /**
   * Adds coreference annotation to the CAS.
   *
   * @param jCas the CAS to process
   *
   * @throws AnalysisEngineProcessException if a non-recoverable error happened during the analysis
   */
  @throws[AnalysisEngineProcessException]("If a non-recoverable error happened during the analysis")
  def process(jCas: JCas): Unit = {

    // set the language pack depending on the CAS language
    // since process is called only once per AE, we can savely modify our config clone
    configClone.setLanguagePack(createLanguagePackForISOLanguage(jCas.getDocumentLanguage))

    val cas = jCas.getCas

    modelProvider.configure(cas)
    numberGenderComputerProvider.configure(cas)

    val model = modelProvider.getResource
    val numberGenderComputer = numberGenderComputerProvider.getResource
    configClone.setNumberGenderComputer(numberGenderComputer)

    val propertyComputer = CorefSystem.createPropertyComputer(configClone)
    val scorer = model.scorer.clone(propertyComputer)

    try {
      CorefSystem.ensureThesauriPresent(model, propertyComputer)
    } catch {
      case e: ThesaurusFeaturesMissingException => throw new ResourceInitializationException(e)
    }

    // convert the CAS to a base document
    val (baseDoc, sentenceToWordToTokenIdx) = docConverter.convert(jCas)

    // predict mentions and prepare entity prediction
    val corefDoc = assembler.createCorefDoc(baseDoc, propertyComputer)
    val docGraph = new DocumentGraph(corefDoc, false)

    // featurize documents
    DocumentGraph.pruneEdgesAll(Seq(docGraph), new PruningStrategy(configClone.pruningStrategy), scorer)
    featurizerTrainer.featurizeBasic(Seq(docGraph), scorer.featurizer)

    // decode weights
    val (_, allPredClusterings) = basicInferencer.viterbiDecodeAllFormClusterings(Seq(docGraph),
      scorer)

    require(allPredClusterings.size == 1, "Input one document, inferencer should return one document in return")

    var clusteringBound = new OrderedClusteringBound(docGraph.getMentions(), allPredClusterings(0))

    if (configClone.removeSingletons) clusteringBound = clusteringBound.postprocessForConll()

    annotateJCas(jCas, clusteringBound, sentenceToWordToTokenIdx)

    // close the connection as we load the thesauri at each process step anew
    if (propertyComputer.thesauri != null) propertyComputer.thesauri.closeConnections()
  }

  /**
   * Interprets the coreference clusters returned by the Berkeley system and adds the appropriate annotations to
   * the CAS.
   *
   * @param jCas the jCas to add annotations to
   * @param clustering the clusterings returned by the Berkeley system
   * @param sentenceToWordToTokenIdx a mapping from sentence indices to relative word indices to corresponding token
   *                                 annotations <b>from the same jCas</b>
   */
  protected def annotateJCas(jCas: JCas, clustering: OrderedClusteringBound, sentenceToWordToTokenIdx: Map[Int,
    Map[Int, Token]]) = {
    for (corefCluster <- clustering.clustering.clusters) {
      val clusterMentions = corefCluster.map(idx => clustering.ments(idx))
      require(clusterMentions.size >= 1, "at least one mention must be in the clustering")

      val firstLink = createLinkFromMention(jCas, clusterMentions(0), sentenceToWordToTokenIdx)
      firstLink.setReferenceType(clusterMentions(0).mentionType.toString)
      var currentLink = firstLink
      var tmpLink: CoreferenceLink = null


      for (mention <- clusterMentions.drop(1)) {
        tmpLink = createLinkFromMention(jCas, mention, sentenceToWordToTokenIdx)
        tmpLink.setReferenceType(mention.mentionType.toString)
        currentLink.setNext(tmpLink)
        currentLink.addToIndexes()
        currentLink = tmpLink
      }

      // add the last mention to the JCas
      if (tmpLink != null) {
        tmpLink.addToIndexes()
      }

      val chain = new CoreferenceChain(jCas)
      chain.setFirst(firstLink)
      chain.addToIndexes()
    }
  }

  protected def createLinkFromMention(jCas: JCas, mention: Mention, sentenceToWordToTokenIdx: Map[Int, Map[Int,
    Token]]): CoreferenceLink = {
    new CoreferenceLink(jCas, sentenceToWordToTokenIdx(mention.sentIdx)(mention.startIdx).getBegin,
      sentenceToWordToTokenIdx(mention.sentIdx)(mention.endIdx - 1).getEnd)
  }
}
