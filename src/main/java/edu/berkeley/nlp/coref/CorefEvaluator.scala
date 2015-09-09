package edu.berkeley.nlp.coref

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.lang.{CorefLanguagePack, Language, LanguagePackFactory}
import edu.berkeley.nlp.coref.nchains.DiscourseAnalyzer
import edu.berkeley.nlp.futile.util.Counter

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object CorefEvaluator {
  
  def evaluateAndRenderShort(docGraphs: Seq[DocumentGraph],
                             inferencer: DocumentInferencer,
                             pairwiseScorer: PairwiseScorer,
                             strPrefix: String): String = {
    // Fraction of guys whose backpointers point to something in the correct cluster
    // Needed for the final eval
    val (allPredBackptrs, allPredClusterings) = inferencer.viterbiDecodeAllFormClusterings(docGraphs, pairwiseScorer)
    val (accuracy, accuracyStr) = computeRenderAccuracy(docGraphs, allPredBackptrs)
    var rendered = accuracyStr
    val (pairwiseF1, pairwiseF1Str) = computeRenderPairwisePRF1(docGraphs, allPredBackptrs, allPredClusterings)
    rendered += "\n" + pairwiseF1Str
    rendered += "\n" + computeLinkageStats(docGraphs, allPredBackptrs)
    inferencer.finishPrintStats()
    val renderedGreppable = strPrefix + rendered.replaceAll("\n", "\n" + strPrefix)
    renderedGreppable
  }

  def evaluateAndRender(docGraphs: Seq[DocumentGraph],
                       inferencer: DocumentInferencer,
                       pairwiseScorer: PairwiseScorer,
                       conllEvalScriptPath: String,
                       strPrefix: String,
                       config: CorefSystemConfiguration): String = {
    // Fraction of guys whose backpointers point to something in the correct cluster
    // Needed for the final eval
    val (allPredBackptrs, allPredClusterings) = inferencer.viterbiDecodeAllFormClusterings(docGraphs, pairwiseScorer)
    val (accuracy, accuracyStr) = computeRenderAccuracy(docGraphs, allPredBackptrs)
    var rendered = accuracyStr
    rendered += "\n" + computeRenderSubdivisionAnalysis(docGraphs, allPredBackptrs)
    val (pairwiseF1, pairwiseF1Str) = computeRenderPairwisePRF1(docGraphs, allPredBackptrs, allPredClusterings)
    rendered += "\n" + pairwiseF1Str
    rendered += "\n" + computeLinkageStats(docGraphs, allPredBackptrs)
    rendered += "\n" + computeRenderGenderSwaps(docGraphs, allPredClusterings, config.languagePack)
    if (config.languagePack.getLanguage == Language.ENGLISH) rendered += "\n" + computeRenderPleonasticItPRF1(docGraphs, allPredClusterings)
    rendered += "\n" + computeRenderBridgingPRF1(docGraphs, allPredClusterings)
    // These steps are slow and produce lots of output
    // ENTITY PURITY ANALYSIS
//    if (analysesToPrint.contains("+purity")) {
//      rendered += "\nPURITY ANALYSIS";
//      rendered += "\nPred cluster purities";
//      rendered += "\n" + PurityAnalyzer.computeRenderNewClusterPurities(docGraphs, allPredClusterings);
//      rendered += "\nGold cluster purities";
//      rendered += "\n" + PurityAnalyzer.computeRenderNewClusterPurities(docGraphs, docGraphs.map(_.getOraclePredClustering()));
//    }
//    // DISCOURSE ANALYSIS
    if (config.analysesToPrint.contains("+discourse")) {
      rendered += "\nDISCOURSE ANALYSIS"
      for (i <- 0 until docGraphs.size) {
//        rendered += "\n" + DiscourseAnalyzer.renderGoldPredDiscourseAnalyses(docGraphs(i), allPredBackptrs(i));
        rendered += "\n" + DiscourseAnalyzer.renderGoldDiscourseAnalysis(docGraphs(i))
        rendered += "\n" + DiscourseAnalyzer.renderDiscourseErrorAnalysis(docGraphs(i), allPredBackptrs(i))
      }
      rendered += "\n" + DiscourseAnalyzer.renderAggregateDiscourseErrors(docGraphs, allPredBackptrs)
    }

    // PRUNING ANALYSIS
    if (config.analysesToPrint.contains("+oracle")) {
      rendered += "\nPRUNING/ORACLE ANALYSIS:\n" + evaluatePruning(docGraphs, allPredBackptrs, allPredClusterings, inferencer, pairwiseScorer, conllEvalScriptPath, config.conllOutputDir)
    }
    // FINAL SCORING
    if (pairwiseF1 > 0.20) {
      rendered += "\n" + computeRenderCoNLL(docGraphs, allPredClusterings, conllEvalScriptPath, config.conllOutputDir)
      if (config.printSigSuffStats) {
        rendered += "\nBootstrap stats"
        rendered += "\n" + computeRenderCoNLLIndividual(docGraphs, allPredClusterings, conllEvalScriptPath, config.conllOutputDir)
        rendered += "\nBridging bootstrap stats"
        rendered += "\n" + computeRenderBridgingIndividual(docGraphs, allPredClusterings)

      }
    } else {
      rendered += "\n" + "Too crappy to run CoNLL scorer: " + pairwiseF1
    }
    inferencer.finishPrintStats()
    val renderedGreppable = strPrefix + rendered.replaceAll("\n", "\n" + strPrefix)
    renderedGreppable
  }
  
  def evaluatePruning(docGraphs: Seq[DocumentGraph],
                      allPredBackptrs: Seq[Array[Int]],
                      allPredClusterings: Seq[OrderedClustering],   
                      inferencer: DocumentInferencer,
                      pairwiseScorer: PairwiseScorer,
                      conllEvalScriptPath: String, conllOutputDir: String): String = {
    var rendered = ""
    val trivialCorrect = new Array[Array[Int]](2)
    (0 until trivialCorrect.size).foreach(i => trivialCorrect(i) = Array.fill(3)(0))
    val trivialIncorrect = new Array[Array[Int]](2)
    (0 until trivialIncorrect.size).foreach(i => trivialIncorrect(i) = Array.fill(3)(0))
    val nontrivial = new Array[Array[Int]](2)
    (0 until nontrivial.size).foreach(i => nontrivial(i) = Array.fill(3)(0))
    // Evaluate oracle
    val allOracleBackptrs = new Array[Array[Int]](docGraphs.size)
    val allOracleClusterings = new Array[OrderedClustering](docGraphs.size)
    for (i <- 0 until docGraphs.size) {
      val docGraph = docGraphs(i)
      // Fix up to make oracle, count number of times there's a nontrivial decision
      val doc = docGraphs(i)
      val predBackptrs = allPredBackptrs(i)
      val oracleBackptrs = new Array[Int](doc.size)
      for (j <- 0 until oracleBackptrs.size) {
        val allAntecedents = doc.getAllAntecedentsCurrentPruning(j)
        val goldAntecedents = doc.getGoldAntecedentsUnderCurrentPruningOrEmptySet(j)
        val goldAntecedentsUnpruned = doc.getGoldAntecedentsNoPruning(j)
        require(goldAntecedentsUnpruned.size >= 1)
        val anaphoricIdx = if (goldAntecedentsUnpruned(0) != j) 0 else 1
        val mentType = docGraph.getMention(j).mentionType
        val mentTypeIdx = if (mentType == MentionType.PRONOMINAL) 0 else if (mentType == MentionType.NOMINAL) 1 else 2
        // If the gold has been ruled out, use the guess
        if (goldAntecedents.size == 0) {
          trivialIncorrect(anaphoricIdx)(mentTypeIdx) += 1
          oracleBackptrs(j) = predBackptrs(j)
        } else { // Otherwise oracle is the gold
          if (allAntecedents.size == goldAntecedents.size) {
            trivialCorrect(anaphoricIdx)(mentTypeIdx) += 1
          } else {
            nontrivial(anaphoricIdx)(mentTypeIdx) += 1
          }
          oracleBackptrs(j) = goldAntecedents.last
        }
      }
      allOracleBackptrs(i) = oracleBackptrs
      allOracleClusterings(i) = OrderedClustering.createFromBackpointers(oracleBackptrs)
    }
    for (i <- 0 until 2) {
      val anaphoric = if (i == 0) "Anaphoric" else "Nonanaphoric"
      for (j <- 0 until 3) {
        val mentType = if (j == 0) "pronominal" else if (j == 1) "nominal" else "proper"
        rendered += "\n" + anaphoric + " " + mentType + " trivial correct / trivial incorrect / nontrivial: " + trivialCorrect(i)(j) + " / " + trivialIncorrect(i)(j) + " / " + nontrivial(i)(j)
      }
    }
    val (accuracy, accuracyStr) = computeRenderAccuracy(docGraphs, allOracleBackptrs)
    rendered += "\nOracle " + accuracyStr
    val (pairwiseF1, pairwiseF1Str) = computeRenderPairwisePRF1(docGraphs, allOracleBackptrs, allOracleClusterings)
    rendered += "\nOracle " + pairwiseF1Str
    if (pairwiseF1 > 0.20) {
      rendered += "\nOracle " + computeRenderCoNLL(docGraphs, allOracleClusterings, conllEvalScriptPath, conllOutputDir)
    } else {
      rendered += "\nToo crappy to run CoNLL scorer: " + pairwiseF1
    }
    rendered
  }
  
  // Returns accuracy and string rep
  def computeRenderAccuracy(docGraphs: Seq[DocumentGraph],
                            allPredBackptrs: Seq[Array[Int]]): (Double, String) = {
    var numCorrect = 0
    var numTotal = 0
    var correct = new Array[Array[Int]](2)
    (0 until correct.size).foreach(i => correct(i) = Array.fill(3)(0))
    var total = new Array[Array[Int]](2)
    (0 until total.size).foreach(i => total(i) = Array.fill(3)(0))
    var numCorrectInformedProns = 0
    var numTotalInformedProns = 0
    var numCorrectBridging = 0
    var numTotalBridging = 0
    var numCorrectNomMisleadingHM = 0
    var numTotalNomMisleadingHM = 0
    val topNomMisleadingHM = new Counter[String]()
    var numCorrectPropMisleadingHM = 0
    var numTotalPropMisleadingHM = 0
    val topPropMisleadingHM = new Counter[String]()
    for (docIdx <- 0 until docGraphs.size) {
      val docGraph = docGraphs(docIdx)
      for (i <- 0 until docGraph.size) {
        // Accuracy
        val predBackptr = allPredBackptrs(docIdx)(i)
        val goldBackptrs = docGraph.getGoldAntecedentsNoPruning(i)
        numTotal += 1
        val anaphoricIdx = if (goldBackptrs(0) != i) 0 else 1
        val mentType = docGraph.getMention(i).mentionType
        val mentTypeIdx = if (mentType == MentionType.PRONOMINAL) 0 else if (mentType == MentionType.NOMINAL) 1 else 2
        val isCorrect = goldBackptrs.contains(predBackptr)
        if (isCorrect) {
          numCorrect += 1
          correct(anaphoricIdx)(mentTypeIdx) += 1
        }
        // Bridging anaphora
        if (mentType == MentionType.NOMINAL || mentType == MentionType.PROPER) {
          val goldAntecedents = docGraph.getGoldAntecedentsNoPruning(i)
          val previousMents = goldAntecedents.map(docGraph.getMention)
          val hasPreviousNominalProper = previousMents.exists(ment => ment.mentionType == MentionType.NOMINAL || ment.mentionType == MentionType.PROPER)
          val hasHeadMatch = previousMents.exists(ment => ment.headStringLc.equals(docGraph.getMention(i).headStringLc))
          if (hasPreviousNominalProper && !hasHeadMatch) {
            if (isCorrect) {
              numCorrectBridging += 1
            }
            numTotalBridging += 1
          }
        }
        // Misleading head matches, all
        if (mentType == MentionType.NOMINAL || mentType == MentionType.PROPER) {
          val goldAntecedents = docGraph.getGoldAntecedentsNoPruning(i)
          val mentHead = docGraph.getMention(i).headStringLc
          var misleadingHeadMatch = false
          for (j <- 0 until i) {
            if (!goldAntecedents.contains(j) && docGraph.getMention(j).headStringLc == mentHead) {
              misleadingHeadMatch = true
              if (mentType == MentionType.NOMINAL) topNomMisleadingHM.incrementCount(mentHead, 1.0) else topPropMisleadingHM.incrementCount(mentHead, 1.0)

            }
          }
          if (misleadingHeadMatch) {
            if (isCorrect) {
              if (mentType == MentionType.NOMINAL) numCorrectNomMisleadingHM += 1 else numCorrectPropMisleadingHM += 1
            }
            if (mentType == MentionType.NOMINAL) numTotalNomMisleadingHM += 1 else numTotalPropMisleadingHM += 1
          }
        }
        // Informed pronouns
//        if (mentType == MentionType.PRONOMINAL) {
//          val mentInContext = docGraph.getMentionInContext(docGraph.getMention(i));
//          val dumbProtoMention = ProtoMention.constructDumbFromMentionWithSrl(mentInContext.sentAndParse, mentInContext.srlPredicates, mentInContext.mention, false);
//          if (dumbProtoMention.verbStr != ProtoMention.NoVerbString) {
//            if (isCorrect) {
//              numCorrectInformedProns += 1;
//            }
//            numTotalInformedProns += 1;
//          }
//        }
        total(anaphoricIdx)(mentTypeIdx) += 1
      }
    }
    topNomMisleadingHM.keepTopNKeys(20)
    topPropMisleadingHM.keepTopNKeys(20)
    val accuracy = numCorrect.toDouble / numTotal.toDouble
    val accStr: (Int, Int) => String = (i, j) => correct(i)(j) + "/" + total(i)(j) + " = " + correct(i)(j).toDouble/total(i)(j).toDouble

    (accuracy, "Accuracy: " + numCorrect + "/" + numTotal + " = " + accuracy +
        "\nAnaphoric pronominals " + accStr(0, 0) + ", Anaphoric nominals " + accStr(0, 1) + ", Anaphoric propers " + accStr(0, 2) +
        "\nNonanaphoric pronominals " + accStr(1, 0) + ", Nonanaphoric nominals " + accStr(1, 1) + ", Nonanaphoric propers " + accStr(1, 2) +
        //"\nInformed pronouns " + numCorrectInformedProns + "/" + numTotalInformedProns + " = " + numCorrectInformedProns.toDouble/numTotalInformedProns.toDouble +
        "\nMisleading headmatch: nominal: " + numCorrectNomMisleadingHM + "/" + numTotalNomMisleadingHM + " = " + numCorrectNomMisleadingHM.toDouble/numTotalNomMisleadingHM.toDouble + ", top misleading: " + topNomMisleadingHM +
        "\n                      proper: " + numCorrectPropMisleadingHM + "/" + numTotalPropMisleadingHM + " = " + numCorrectPropMisleadingHM.toDouble/numTotalPropMisleadingHM.toDouble + ", top misleading: " + topPropMisleadingHM +
        "\nQuasi-bridging anaphora " + numCorrectBridging + "/" + numTotalBridging + " = " + numCorrectBridging.toDouble/numTotalBridging.toDouble)

  }
  
  def computeRenderSubdivisionAnalysis(docGraphs: Seq[DocumentGraph],
                                       allPredBackptrs: Seq[Array[Int]]): String = {
    val correctCounter = new Counter[Seq[String]]
    val totalCounter = new Counter[Seq[String]]
    for (docIdx <- 0 until docGraphs.size) {
      val docGraph = docGraphs(docIdx)
      for (i <- 0 until docGraph.size) {
        // Is it correct?
        val predBackptr = allPredBackptrs(docIdx)(i)
        val goldBackptrs = docGraph.getGoldAntecedentsNoPruning(i)
        val isCorrect = goldBackptrs.contains(predBackptr)
        val properties = new ArrayBuffer[String]()
        // Identify anaphoric vs. starting new vs. singleton
        val goldClustering = docGraph.getOraclePredClustering()
        val antecedents = goldClustering.getAllAntecedents(i)
        val consequents = goldClustering.getAllConsequents(i)
        val mentType = (if (docGraph.getMention(i).mentionType == MentionType.PRONOMINAL) "pron" else "nomprop")
        val structuralType = if (antecedents.size > 0) "anaphoric" else if (consequents.size > 0) "new" else "singleton"
        properties += mentType
        properties += structuralType
        // Nominal/proper splits
        if (mentType == "nomprop") {
          var goodHeadMatch = false
          var misleadingHeadMatch = false
          for (j <- 0 until i) {
            val antMent = docGraph.getMention(j)
            if (antMent.headStringLc == docGraph.getMention(i).headStringLc) {
              if (antecedents.contains(j)) {
                goodHeadMatch = true
              } else {
                misleadingHeadMatch = true
              }
            }
          }
          if (structuralType == "anaphoric") {
            properties += (if (goodHeadMatch || misleadingHeadMatch) "not-1st-occ" else "1st-occ")
            properties += (if (goodHeadMatch) "head-match" else "no-head-match")
            properties += (if (misleadingHeadMatch) "misleading" else "no-misleading")
          } else {
            properties += (if (goodHeadMatch || misleadingHeadMatch) "not-1st-occ" else "1st-occ")
            properties += (if (misleadingHeadMatch) "misleading" else "no-misleading")
          }
        } else { // Pronominal splits
          if (structuralType == "anaphoric") {
            val immediateAntecedentType = docGraph.getMention(antecedents.last).mentionType
            properties += (if (immediateAntecedentType == MentionType.PRONOMINAL) "pron-ant" else "nomprop-ant")
          }
        }
        // Increment all subsequences of the properties
        for (i <- 1 to properties.size) {
          if (isCorrect) {
            correctCounter.incrementCount(properties.slice(0, i), 1.0)
          }
          totalCounter.incrementCount(properties.slice(0, i), 1.0)
        }
      }
    }
    var renderedStr = "Analysis:\n"

    val sortedKeys = totalCounter.keySet.asScala.toSeq.sortBy(_.mkString(", "))
    // Sort the keys...this seems way harder than it has to be...
    /*val sortedKeys = totalCounter.keySet.asScala.toSeq.sortWith((a, b) => {
      var firstDiffIdx = -1;
      for (i <- 0 until Math.min(a.size, b.size)) {
        if (firstDiffIdx == -1 && a(i) != b(i)) {
          firstDiffIdx = i;
        }
      }
      // Never different
      if (firstDiffIdx == -1) {
        a.size < b.size; // return the shorter;
      } else {
        a(firstDiffIdx) < b(firstDiffIdx);
      }
    });*/
    for (key <- sortedKeys) yield {
      renderedStr += "   " + key.toString + ": " + correctCounter.getCount(key) + " / " + totalCounter.getCount(key) + " = " + (correctCounter.getCount(key)/totalCounter.getCount(key)) + "\n"

    }
    renderedStr
  }
  
  // Returns F1 and string rep
  def computeRenderPairwisePRF1(docGraphs: Seq[DocumentGraph],
                                allPredBackptrs: Seq[Array[Int]],
                                allPredClusterings: Seq[OrderedClustering]): (Double, String) = {
    var numPairsCorrect = 0
    var predNumPairs = 0
    var goldNumPairs = 0
    for (docIdx <- 0 until docGraphs.size) {
      val oraclePredClustering = docGraphs(docIdx).getOraclePredClustering
      for (i <- 0 until allPredBackptrs(docIdx).size) {
        // Pairwise F1
        for (j <- 0 until i) {
          val predEdge = allPredClusterings(docIdx).areInSameCluster(i, j)
          val goldEdge = oraclePredClustering.areInSameCluster(i, j)
          if (predEdge && goldEdge) {
            numPairsCorrect += 1
          }
          if (predEdge) {
            predNumPairs += 1
          }
          if (goldEdge) {
            goldNumPairs += 1
          }
        }
      }
    }
    val pairwisePrec = numPairsCorrect.toDouble / predNumPairs.toDouble
    val pairwiseRec = numPairsCorrect.toDouble / goldNumPairs.toDouble
    val pairwiseF1 = (2 * pairwisePrec * pairwiseRec / (pairwisePrec + pairwiseRec))
    (pairwiseF1, "Pairwise P/R/F1: " + numPairsCorrect + "/" + predNumPairs + " = " + pairwisePrec + ", " +
          numPairsCorrect + "/" + goldNumPairs + " = " + pairwiseRec + ", " + pairwiseF1)
  }
  
  def computeLinkageStats(docGraphs: Seq[DocumentGraph],
                          allPredBackptrs: Seq[Array[Int]]): String = {
    var nomPropToNomProp = 0
    var nomPropToDiffNomProp = 0
    var nomPropToPron = 0
    var pronToNomProp = 0
    var pronToPron = 0
    var pronToDiffPron = 0
    val linkageCounts = Array.tabulate(3, 4)((i, j) => 0)
    for (i <- 0 until docGraphs.size) {
      val docGraph = docGraphs(i)
      val predBackptrs = allPredBackptrs(i)
      for (j <- 0 until docGraph.size) {
        val backptr = predBackptrs(j)
        if (backptr != j) {
          val currIsPron = docGraph.getMention(j).mentionType == MentionType.PRONOMINAL
          val prevIsPron = docGraph.getMention(backptr).mentionType == MentionType.PRONOMINAL
          if (currIsPron && prevIsPron) {
            pronToPron += 1
            if (!docGraph.getMention(j).headStringLc.equals(docGraph.getMention(backptr).headStringLc)) {
              pronToDiffPron += 1
            }
          } else if (currIsPron && !prevIsPron) {
            pronToNomProp += 1
          } else if (!currIsPron && prevIsPron) {
            nomPropToPron += 1
          } else if (!currIsPron && !prevIsPron) {
            nomPropToNomProp += 1
            if (!docGraph.getMention(j).headStringLc.equals(docGraph.getMention(backptr).headStringLc)) {
              nomPropToDiffNomProp += 1
            }
          }
        }
      }
    }
    "Linkage counts: NP->NP: " + nomPropToNomProp + " (" + nomPropToDiffNomProp + " different), P->NP: " + pronToNomProp + ", NP->P: " + nomPropToPron + ", P->P: " + pronToPron + " (" + pronToDiffPron + " different)"

  }

  /**
   * Counts how many "obvious" gender swapping pronouns are produced, e.g. "he" having a backpointer to "she".
   * The decision will be checked against the gold clustering, as the heuristic may be not precise enough.
   * If the gold clustering of the mention is also gender swapping, the mention will be forgiven.
   * ignored.
   *
   * @param docGraphs
   * @param allPredClusterings
   */
  def computeRenderGenderSwaps(docGraphs: Seq[DocumentGraph],
                               allPredClusterings: Seq[OrderedClustering], languagePack: CorefLanguagePack) = {
    var genderSwappingPronouns = 0
    var totalPronouns = 0
    val badTargetCounter = new Counter[(String, String)]
    val pronounOriginCounter = new Counter[String]

    val language = languagePack

    for (docIdx <- 0 until docGraphs.size) {
      val docGraph = docGraphs(docIdx)
      val predClustering = allPredClusterings(docIdx)

      for (mentIdx <- 0 until docGraph.size) {
        val mention = docGraph.getMention(mentIdx)
        val goldAntecedents = docGraph.getGoldAntecedentsNoPruning(mentIdx)

        if (mention.mentionType == MentionType.PRONOMINAL) {
          totalPronouns += 1

          val lemmatizedPronoun = mention.headStringLemma.toLowerCase

          pronounOriginCounter.incrementCount(lemmatizedPronoun, 1.0)

          val genderSwappingTarget = predClustering.getAllAntecedents(mentIdx).map(docGraph.getMention).filter(_.mentionType ==
            MentionType.PRONOMINAL).reverse.find(antMention => !language.pronounsAgreeGender(lemmatizedPronoun,
            antMention.headStringLemma.toLowerCase))

          if (genderSwappingTarget.nonEmpty) {
            val goldClusterIsGenderSwapping = goldAntecedents.map(docGraph.getMention).filter(_.mentionType ==
              MentionType.PRONOMINAL).exists(antMention => !language.pronounsAgreeGender(lemmatizedPronoun,
              antMention.headStringLemma.toLowerCase))

            if (!goldClusterIsGenderSwapping) {
              genderSwappingPronouns += 1
              badTargetCounter.incrementCount((lemmatizedPronoun,
                genderSwappingTarget.get.headStringLemma.toLowerCase), 1.0)
            }
          }
        }
      }
    }

    "Gender swapping pronouns (less is better):\n" + genderSwappingPronouns + "/" + totalPronouns + " = " + (genderSwappingPronouns.toDouble / totalPronouns.toDouble) +
      " Individual targets (pronoun/last genderswapping pronoun in same cluster): " + badTargetCounter.keySet().asScala.toSeq.sorted.map { pair =>
      val pairCount = badTargetCounter.getCount(pair)
      val pronounCount = pronounOriginCounter.getCount(pair._1)
      pair._1 + " -> " + pair._2 + ": " + pairCount + "/" + pronounCount + " = " + (pairCount / pronounCount)
    }.mkString("\n")
  }

  def computeRenderPleonasticItPRF1(docGraphs: Seq[DocumentGraph],
                                   allPredClusterings: Seq[OrderedClustering]) = {
    var truePositives = 0.0
    var falsePositives = 0.0
    var falseNegatives = 0.0

    for (docIdx <- 0 until docGraphs.size) {
      val docGraph = docGraphs(docIdx)
      val predClustering = allPredClusterings(docIdx)

      for (mentIdx <- 0 until docGraph.size) {
        val mention = docGraph.getMention(mentIdx)
        val goldAntecedents = docGraph.getGoldAntecedentsNoPruning(mentIdx)

        if (mention.mentionType == MentionType.PRONOMINAL && mention.headStringLc == "it") {
          val isPleonastic = goldAntecedents.size == 1 && goldAntecedents(0) == mentIdx
          val responsePleonastic = predClustering.isSingleton(mentIdx)

          if (isPleonastic && responsePleonastic) truePositives += 1
          if (isPleonastic && !responsePleonastic) falseNegatives += 1
          if (!isPleonastic && responsePleonastic) falsePositives += 1
        }
      }
    }

    val precision = truePositives / (truePositives + falsePositives)
    val recall = truePositives / (truePositives + falseNegatives)
    val f1 = (2 * precision * recall) / (precision + recall)

    "Pleonastic it Precision: " + truePositives + "/" + (truePositives + falsePositives) + " = " + precision + "; Recall: " +
    truePositives + "/" + (truePositives + falseNegatives) + " = " + recall + "; F1: " + f1
  }

  def computeRenderBridgingPRF1(docGraphs: Seq[DocumentGraph], allPredClusterings: Seq[OrderedClustering]) = {
    var recalledBridging = 0.0
    var totalBridging = 0.0
    var falseNewBridging = 0.0

    for (docIdx <- 0 until docGraphs.size) {
     val (thisDocTotalBridging, thisDocRecalledBridging, thisDocFalseNewBridging) = computeBridgingStatsForDocument(docGraphs(docIdx), allPredClusterings(docIdx))
      totalBridging += thisDocTotalBridging
      recalledBridging += thisDocRecalledBridging
      falseNewBridging += thisDocFalseNewBridging
    }

    val precision = recalledBridging / (recalledBridging + falseNewBridging)
    val recall = recalledBridging / totalBridging
    val f1 = (2 * precision * recall) / (precision + recall)

    "Bridging Precision: %d/%d = %f, Recall: %d/%d = %f, F1: %f".format(recalledBridging.toInt, (recalledBridging+falseNewBridging).toInt, precision,
                                                                        recalledBridging.toInt, totalBridging.toInt, recall,
                                                                        f1)
  }

  def computeBridgingStatsForDocument(docGraph: DocumentGraph, predClustering: OrderedClustering): (Int, Int, Int) = {
    var totalBridging = 0
    var recalledBridging = 0
    var falseNewBridging = 0

    for (mentIdx <- 0 until docGraph.size) {
      val currMention = docGraph.getMention(mentIdx)
      val previousGoldMents = docGraph.getGoldAntecedentsNoPruning(mentIdx).filter(_ != mentIdx).map(docGraph.getMention).reverse
      val previousSystemMents = predClustering.getAllAntecedents(mentIdx).filter(_ != mentIdx).map(docGraph.getMention).reverse

      if (currMention.mentionType == MentionType.NOMINAL || currMention.mentionType == MentionType.PROPER) {
        val previousNominalProperGold = previousGoldMents.find(ment => ment.mentionType == MentionType.NOMINAL || ment.mentionType == MentionType.PROPER)
        val hasHeadMatchGold = previousGoldMents.exists(ment => ment.headStringLc.equals(currMention.headStringLc))
        if (previousNominalProperGold.isDefined && !hasHeadMatchGold) {
          totalBridging += 1
          if (predClustering.areInSameCluster(previousNominalProperGold.get.mentIdx, currMention.mentIdx)) recalledBridging += 1
        }

        val previousNominalProperSystem = previousSystemMents.find(ment => ment.mentionType == MentionType.NOMINAL || ment.mentionType == MentionType.PROPER)
        val hasHeadMatchSystem = previousSystemMents.exists(ment => ment.headStringLc.equals(currMention.headStringLc))
        if (previousNominalProperSystem.isDefined && !hasHeadMatchSystem) {
          val isCorrect = docGraph.isGoldNoPruning(currMention.mentIdx, previousNominalProperSystem.get.mentIdx)
          if (!isCorrect) falseNewBridging += 1
        }
      }
    }

    (totalBridging, recalledBridging, falseNewBridging)
  }
  
  
  def computeRenderCoNLLIndividual(docGraphs: Seq[DocumentGraph],
                                   allPredClusterings: Seq[OrderedClustering],
                                   conllEvalScriptPath: String, conllOutputDir: String): String = {
    var str = ""
    for (i <- 0 until docGraphs.size) {
//      val conllStr = new CorefConllScorer(conllEvalScriptPath).renderSuffStats(docGraphs(i).corefDoc.rawDoc, allPredClusterings(i), docGraphs(i).corefDoc.goldClustering);
      val pc = new OrderedClusteringBound(docGraphs(i).getMentions(), allPredClusterings(i))
      val gc = new OrderedClusteringBound(docGraphs(i).corefDoc.goldMentions, docGraphs(i).corefDoc.goldClustering)
      val conllStr = new CorefConllScorer(conllEvalScriptPath, conllOutputDir).renderSuffStats(docGraphs(i).corefDoc.rawDoc, pc, gc)

      str += i + ": " + conllStr + "\n"
    }
    str
  }

  def computeRenderBridgingIndividual(docGraphs: Seq[DocumentGraph], allPredClusterings: Seq[OrderedClustering]): String = {
    val sb = new StringBuilder

    for (i <- 0 until docGraphs.size) {
        val (thisDocTotalBridging, thisDocRecalledBridging, thisDocFalseNewBridging) = computeBridgingStatsForDocument(docGraphs(i), allPredClusterings(i))

      sb ++= "%d: Bridging P/R N/D:\t%d\t%d\t%d\t%d\n".format(i, thisDocRecalledBridging, thisDocRecalledBridging + thisDocFalseNewBridging, thisDocRecalledBridging, thisDocTotalBridging)
    }

    sb.toString()
  }
  
  def computeRenderCoNLL(docGraphs: Seq[DocumentGraph],
                         allPredClusterings: Seq[OrderedClustering],
                         conllEvalScriptPath: String, conllOutputDir: String): String = {
//    val conllStr = new CorefConllScorer(conllEvalScriptPath).renderFinalScore(docGraphs.map(_.corefDoc.rawDoc), allPredClusterings, docGraphs.map(_.corefDoc.goldClustering));
    val pcs = (0 until docGraphs.size).map(i => new OrderedClusteringBound(docGraphs(i).getMentions, allPredClusterings(i)))

    val gcs = docGraphs.map(graph => new OrderedClusteringBound(graph.corefDoc.goldMentions, graph.corefDoc.goldClustering))
    val conllStr = new CorefConllScorer(conllEvalScriptPath, conllOutputDir).renderFinalScore(docGraphs.map(_.corefDoc.rawDoc), pcs, gcs)

    "CoNLL score: " + conllStr
  }
  
//  def printAnalysis(docGraphs: Seq[DocumentGraph],
//                    allPredBackptrs: Seq[Array[Int]],
//                    allPredClusterings: Seq[BClustering[Mention]],
//                    inferencer: DocumentInferencer,
//                    pairwiseScorer: PairwiseScorer,
//                    doPostprocessingFilters: Boolean) {
//    val docGraphIndicesToAnalyze = getDocIndicesToAnalyze(docGraphs);
//    Logger.logss("Analysis for: " + docGraphIndicesToAnalyze.map(docGraphs(_).doc.getDocID()));
//    for (i <- docGraphIndicesToAnalyze) {
//      Logger.logss("Analysis for " + docGraphs(i).doc.getDocID());
//      val postProcessedClustering = docGraphs(i).doc.postProcessClustering(allPredClusterings(i), doPostprocessingFilters);
//      val auxiliaryInfo: Array[String] = CrfAnalyzer.getAuxiliaryInfo(docGraphs(i), pairwiseScorer, inferencer, allPredBackptrs(i));
//      Analyzer.renderAndPrintDoc(docGraphs(i).doc, postProcessedClustering, auxiliaryInfo);
//    }
//  }
//  
//  def decodePrintPruningAnalysis(docGraphs: Seq[DocumentGraph],
//                                 inferencer: DocumentInferencer,
//                                 pairwiseScorer: PairwiseScorer,
//                                 doPostprocessingFilters: Boolean) {
//    val (allPredBackptrs, allPredClusterings) = inferencer.viterbiDecodeAllFormClusterings(docGraphs, pairwiseScorer)
//    printPruningAnalysis(docGraphs, allPredBackptrs, allPredClusterings, inferencer, pairwiseScorer, doPostprocessingFilters)
//  }
//  
//  def printPruningAnalysis(docGraphs: Seq[DocumentGraph],
//                           allPredBackptrs: Seq[Array[Int]],
//                           allPredClusterings: Seq[BClustering[Mention]],
//                           inferencer: DocumentInferencer,
//                           pairwiseScorer: PairwiseScorer,
//                           doPostprocessingFilters: Boolean) {
//    val docGraphIndicesToAnalyze = getDocIndicesToAnalyze(docGraphs);
//    Logger.logss("Pruning analysis for: " + docGraphIndicesToAnalyze.map(docGraphs(_).doc.getDocID()));
//    for (i <- docGraphIndicesToAnalyze) {
//      Logger.logss("Pruning analysis for " + docGraphs(i).doc.getDocID());
//      val postProcessedClustering = docGraphs(i).doc.postProcessClustering(allPredClusterings(i), doPostprocessingFilters);
//      val auxiliaryInfo: Array[String] = CrfAnalyzer.getPruningInfo(docGraphs(i), pairwiseScorer, allPredBackptrs(i));
//      Analyzer.renderAndPrintDoc(docGraphs(i).doc, postProcessedClustering, auxiliaryInfo);
//    }
//  }
//  
//  private def getDocIndicesToAnalyze(docGraphs: Seq[DocumentGraph]): Seq[Int] = {
//    // Get one doc of each type
//    val docGraphIndicesToAnalyze = new ArrayBuffer[Int]();
//    val docTypesRepresented = new HashSet[String]();
//    for (i <- 0 until docGraphs.size) {
//      val docGraph = docGraphs(i);
//      val docId = docGraph.doc.getDocID();
//      val docType = docId.substring(0, docId.indexOf("_"));
//      if (!docTypesRepresented.contains(docType)) {
//        docGraphIndicesToAnalyze += i;;
//        docTypesRepresented += docType;
//      }
//    }
//    docGraphIndicesToAnalyze;
//  }
}