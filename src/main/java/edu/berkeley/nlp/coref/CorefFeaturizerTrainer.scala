package edu.berkeley.nlp.coref

import edu.berkeley.nlp.coref.bp.DocumentFactorGraph
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.futile.fig.basic.SysInfoUtils
import edu.berkeley.nlp.futile.util.Logger

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable.HashSet


class CorefFeaturizerTrainer(config: CorefSystemConfiguration) {
  var inferenceNanos = 0L;
  var adagradNanos = 0L;

  def featurizeBasic(docGraphs: Seq[DocumentGraph], pairwiseIndexingFeaturizer: PairwiseIndexingFeaturizer) {
    Logger.logss("Pairwise feats enabled for featurization: " + pairwiseIndexingFeaturizer.getPairwiseFeatsEnabled)
    val featureIndexer = pairwiseIndexingFeaturizer.getIndexer;
    // Do all preprocessing of the training set necessary to compute features
    Logger.logss("Memory before featurization: " + SysInfoUtils.getUsedMemoryStr());
    Logger.startTrack("Featurizing (basic pass)");
    var idx = 0;
    for (docGraph <- docGraphs) {
      if (idx % 5 == 0) {
        Logger.logs("Featurizing (basic pass) " + idx + ", " + SysInfoUtils.getUsedMemoryStr() + ", " +
          "" + featureIndexer.size());
      }
      docGraph.featurizeIndexNonPrunedUseCache(pairwiseIndexingFeaturizer);
      idx += 1;
      if (pairwiseIndexingFeaturizer.mentionPropertyComputer.thesauri != null) {
        pairwiseIndexingFeaturizer.mentionPropertyComputer.thesauri.all.foreach(_.clearCache())
      }
    }
    Logger.endTrack();
    Logger.logss("Features after featurization: " + featureIndexer.size());
    Logger.logss("\"Topic\" features after featurization: " +
      featureIndexer.getObjects.asScala.count(_.contains("Topic")))
    Logger.logss("\"Distrib\" features after featurization: " +
      featureIndexer.getObjects.asScala.count(_.contains("Distrib")))
    if (pairwiseIndexingFeaturizer.mentionPropertyComputer.thesauri != null) {
      for (thesaurus <- pairwiseIndexingFeaturizer.mentionPropertyComputer.thesauri.all) {
        Logger.logss("Thesaurus " + thesaurus.identifier + " statistics:")
        Logger.logss("Head in prior jo expansions: " + (thesaurus.priorTermExpansionFindings.toDouble / thesaurus
          .priorTermExpansionTrials.toDouble))
        if (thesaurus.attributeTermExpansionTrials > 0) Logger.logss("Properties in prior jo expansions: " + (thesaurus
          .attributeTermExpansionFindings.toDouble / thesaurus.attributeTermExpansionTrials.toDouble))
        if (thesaurus.priorTermExpansionFindingsFrequency != null) {
          Logger.logss("Top 100 findings: " + (thesaurus.priorTermExpansionFindingsFrequency.foldLeft(Map[String,
            Int]() withDefaultValue 0) {
            (m, x) => m + (x -> (1 + m(x)))
          }).view.toList.sortBy(-_._2).take(100))

          if (thesaurus.contextScores != null && thesaurus.contextScores.nonEmpty) {
            Logger.logss("Top 100 context scores: " + (thesaurus.contextScores.toList.sortBy(-_._2).take(100)))
          }
        }
      }

      Logger.logss("First 100 thesaurus features:" + featureIndexer.getObjects.asScala.filter(_.startsWith("DT"))
        .take(100))
    }
    docGraphs(0).printAverageFeatureCountInfo();
    Logger.logss("Memory after featurization: " + SysInfoUtils.getUsedMemoryStr());
  }

  def featurizeRahmanAddToIndexer(docGraphs: Seq[DocumentGraph], featurizer: PairwiseIndexingFeaturizer,
                                  entityFeaturizer: EntityFeaturizer) {
    // Could potentially do this for all DocumentGraphs but right now the
    // only information used is the number of clusterers and the sizes of their domains
    val feats = entityFeaturizer.getAllPossibleFeatures(docGraphs(0));
    feats.map(feat => featurizer.getIndex(feat.name, true));
  }

  def featurizeLoopyAddToIndexer(docGraphs: Seq[DocumentGraph], featurizer: PairwiseIndexingFeaturizer) {
    val newFeatures = new HashSet[String]();
    for (docGraph <- docGraphs) {
      val docFactorGraph = new DocumentFactorGraph(docGraph, featurizer, config, false);
      newFeatures ++= docFactorGraph.allFeatures;
    }
    newFeatures.map(featurizer.getIndex(_, true));
  }

  def train(trainDocGraphs: Seq[DocumentGraph],
            pairwiseIndexingFeaturizer: PairwiseIndexingFeaturizer,
            eta: Double,
            reg: Double,
            lossFcn: (CorefDoc, Int, Int) => Double,
            numItrs: Int,
            inferencer: DocumentInferencer): Array[Double] = {
    trainAdagrad(trainDocGraphs, pairwiseIndexingFeaturizer, eta, reg, lossFcn, numItrs, inferencer);
  }

  def trainAdagrad(trainDocGraphs: Seq[DocumentGraph],
                   pairwiseIndexingFeaturizer: PairwiseIndexingFeaturizer,
                   eta: Double,
                   lambda: Double,
                   lossFcn: (CorefDoc, Int, Int) => Double,
                   numItrs: Int,
                   inferencer: DocumentInferencer): Array[Double] = {
    //    val weights = Array.fill(pairwiseIndexingFeaturizer.featureIndexer.size)(0.0);
    val weights = inferencer.getInitialWeightVector(pairwiseIndexingFeaturizer.getIndexer);
    val reusableGradientArray = Array.fill(pairwiseIndexingFeaturizer.getIndexer.size)(0.0);
    val diagGt = Array.fill(pairwiseIndexingFeaturizer.getIndexer.size)(0.0);
    for (i <- 0 until numItrs) {
      Logger.logss("ITERATION " + i);
      val startTime = System.nanoTime();
      inferenceNanos = 0;
      adagradNanos = 0;
      Logger.startTrack("Computing gradient");
      for (exAndIndex <- trainDocGraphs.view.zipWithIndex) {
        Logger.logs("Computing gradient on " + exAndIndex._2);
        takeAdagradStepL1R(exAndIndex._1,
          inferencer,
          new PairwiseScorer(pairwiseIndexingFeaturizer, weights),
          reusableGradientArray,
          diagGt,
          eta,
          lambda,
          lossFcn,
          weights);
        require(!GUtil.containsNaNOrNegInf(weights));
      }
      Logger.endTrack();
      Logger.logss("NONZERO WEIGHTS: " + weights.foldRight(0)((weight, count) => if (Math.abs(weight) > 1e-15) count
        + 1
      else count));
      Logger.logss("WEIGHT VECTOR NORM: " + weights.foldRight(0.0)((weight, norm) => norm + weight * weight));
      weights.foreach(weight => require(weight != Double.NegativeInfinity && weight != Double.NaN));
      //      Logger.logss("ZIPPED: " + featureIndexer.zip(weights))
      if (i == 0 || i == 1 || i % 5 == 4 || i == numItrs - 1) {
        val pairwiseScorer = new PairwiseScorer(pairwiseIndexingFeaturizer, weights);
        Logger.startTrack("Evaluating objective on train");
        Logger.logss("TRAIN OBJECTIVE: " + computeObjectiveL1R(trainDocGraphs, inferencer, pairwiseScorer, lossFcn,
          lambda));
        Logger.endTrack();
        Logger.startTrack("Decoding train");
        val trainAcc = CorefEvaluator.evaluateAndRenderShort(trainDocGraphs, inferencer, pairwiseScorer, "TRAIN: ");
        Logger.logss(trainAcc);
        Logger.endTrack();
      }
      Logger.logss("MILLIS FOR ITER " + i + ": " + (System.nanoTime() - startTime) / 1000000.0);
      Logger.logss("MILLIS INFERENCE FOR ITER " + i + ": " + inferenceNanos / 1000000.0);
      Logger.logss("MILLIS ADAGRAD FOR ITER " + i + ": " + adagradNanos / 1000000.0);
      Logger.logss("MEMORY AFTER ITER " + i + ": " + SysInfoUtils.getUsedMemoryStr());
    }
    weights
  }

  //  def trainLbfgsL2R(trainDocGraphs: Seq[DocumentGraph],
  //                    pairwiseIndexingFeaturizer: PairwiseIndexingFeaturizer2,
  //                    c: Double,
  //                    lossFcn: (CorefDoc, Int, Int) => Double,
  //                    numItrs: Int,
  //                    inferencer: DocumentInferencer): Array[Double] = {
  //    val diffFunc = new CachingDifferentiableFunction() {
  //      def dimension() = pairwiseIndexingFeaturizer.getIndexer.size;
  //
  //      def calculate(weights: Array[Double]) = {
  //        val pairwiseScorer = new PairwiseScorer(pairwiseIndexingFeaturizer, weights);
  //        var objective = 0.0;
  //        val gradient = new Array[Double](dimension());
  //        for (docGraph <- trainDocGraphs) {
  //          inferencer.addUnregularizedStochasticGradient(docGraph, pairwiseScorer, lossFcn, gradient)
  //          objective += inferencer.computeLikelihood(docGraph, pairwiseScorer, lossFcn);
  //        }
  //        for (i <- 0 until gradient.size) {
  //          objective -= c * weights(i) * weights(i);
  //          gradient(i) -= 2 * c * weights(i);
  //        }
  //        // Negate all the things
  //        val negObjective = -objective;
  //        val negGradient = gradient.map(-_);
  //        println("TRAIN OBJECTIVE: " + objective);
  //        println("WEIGHT VECTOR NORM: " + weights.foldRight(0.0)((weight, norm) => norm + weight * weight));
  //        new fig.basic.Pair[java.lang.Double,Array[Double]](negObjective, negGradient);
  //      }
  //    };
  ////    val weights = Array.fill(pairwiseIndexingFeaturizer.featureIndexer.size)(0.0);
  //    val weights = inferencer.getInitialWeightVector(pairwiseIndexingFeaturizer.getIndexer);
  //    // Tolerance shouldn't be too small
  //    val finalWeights = new LBFGSMinimizer(numItrs).minimize(diffFunc, weights, 0.001, true);
  //    val finalScorer = new PairwiseScorer(pairwiseIndexingFeaturizer, finalWeights);
  //    println("TRAIN OBJECTIVE: " + computeObjectiveL2R(trainDocGraphs, inferencer, finalScorer, lossFcn, c));
  //    val trainAcc = CorefEvaluator.evaluateAndRenderShort(trainDocGraphs, inferencer, finalScorer, "TRAIN: ");
  //    Logger.logss(trainAcc);
  //    finalWeights
  //  }

  def computeObjectiveL1R(trainDocs: Seq[DocumentGraph],
                          inferencer: DocumentInferencer,
                          pairwiseScorer: PairwiseScorer,
                          lossFcn: (CorefDoc, Int, Int) => Double,
                          lambda: Double): Double = {
    var objective = computeLikelihood(trainDocs, inferencer, pairwiseScorer, lossFcn);
    for (weight <- pairwiseScorer.weights) {
      objective -= lambda * Math.abs(weight);
    }
    objective;
  }

  //  def computeObjectiveL2R(trainDocs: Seq[DocumentGraph],
  //                          inferencer: DocumentInferencer,
  //                          pairwiseScorer: PairwiseScorer,
  //                          lossFcn: (CorefDoc, Int, Int) => Double,
  //                          c: Double): Double = {
  //    var objective = computeLikelihood(trainDocs, inferencer, pairwiseScorer, lossFcn);
  //    for (weight <- pairwiseScorer.weights) {
  //      objective -= c * weight * weight;
  //    }
  //    objective;
  //  }

  def computeLikelihood(trainDocGraphs: Seq[DocumentGraph],
                        inferencer: DocumentInferencer,
                        pairwiseScorer: PairwiseScorer,
                        lossFcn: (CorefDoc, Int, Int) => Double): Double = {
    trainDocGraphs.foldRight(0.0)((docGraph, likelihood) => likelihood + inferencer.computeLikelihood(docGraph,
      pairwiseScorer, lossFcn));
  }

  def takeAdagradStepL1R(doc: DocumentGraph,
                         inferencer: DocumentInferencer,
                         pairwiseScorer: PairwiseScorer,
                         reusableGradientArray: Array[Double],
                         diagGt: Array[Double],
                         eta: Double,
                         lambda: Double,
                         lossFcn: (CorefDoc, Int, Int) => Double,
                         weights: Array[Double]) {
    for (i <- 0 until reusableGradientArray.length) {
      reusableGradientArray(i) = 0.0;
    }
    var nanoTime = System.nanoTime();
    inferencer.addUnregularizedStochasticGradient(doc, pairwiseScorer, lossFcn, reusableGradientArray);
    inferenceNanos += (System.nanoTime() - nanoTime);
    nanoTime = System.nanoTime();
    (0 until reusableGradientArray.length).par.foreach { i =>
      val xti = pairwiseScorer.weights(i);
      // N.B. We negate the gradient here because the Adagrad formulas are all for minimizing
      // and we're trying to maximize, so think of it as minimizing the negative of the objective
      // which has the opposite gradient
      // Equation (25) in http://www.cs.berkeley.edu/~jduchi/projects/DuchiHaSi10.pdf
      // eta is the step size, lambda is the regularization
      val gti = -reusableGradientArray(i);
      // Update diagGt
      diagGt(i) += gti * gti;
      val Htii = 1 + Math.sqrt(diagGt(i));
      // Avoid divisions at all costs...
      val etaOverHtii = eta / Htii;
      val newXti = xti - etaOverHtii * gti;
      weights(i) = Math.signum(newXti) * Math.max(0, Math.abs(newXti) - lambda * etaOverHtii);
    }
    adagradNanos += (System.nanoTime() - nanoTime);
  }

}