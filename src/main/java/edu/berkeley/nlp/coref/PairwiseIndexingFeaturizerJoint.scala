package edu.berkeley.nlp.coref

import java.io.{IOException, ObjectOutputStream, FileReader}

import org.jobimtext.coref.berkeley.DistributionalThesaurusComputer.AttributeIncompatibilityResult
import org.jobimtext.coref.berkeley.bansalklein.BansalKleinFeaturizer

import scala.collection.mutable.{ListBuffer, ArrayBuffer, HashMap}
import edu.berkeley.nlp.futile.fig.basic.Indexer
import edu.berkeley.nlp.futile.util.Logger
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.coref.preprocess.NerExample
import org.jobimtext.coref.berkeley.{ThesaurusCollection, ThesaurusFeature}
import scala.collection.mutable

@SerialVersionUID(1L)
class PairwiseIndexingFeaturizerJoint(val featureIndexer: Indexer[String],
                                      val featsToUse: String,
                                      val conjType: ConjType,
                                      val dtConjType: ConjType,
                                      val dtRemoveIncompatibleTermsK: Int,
                                      val discretizeIntervalFactor: Double,
                                      val chimergeIntervalsFile: String,
                                      val lexicalCounts: LexicalCountsBundle,
                                      var mentionPropertyComputer: MentionPropertyComputer) extends
PairwiseIndexingFeaturizer with
Serializable {

  val bkFeaturizer = BansalKleinFeaturizer(this)

  /**
   * Creates a copy of this featurizer, and sets its mention property computer to the given value.
   */
  override def clone(computer: MentionPropertyComputer): PairwiseIndexingFeaturizerJoint = {
    new PairwiseIndexingFeaturizerJoint(featureIndexer, featsToUse, conjType, dtConjType, dtRemoveIncompatibleTermsK, discretizeIntervalFactor, chimergeIntervalsFile, lexicalCounts, computer)
  }

  def getIndexer = featureIndexer

  def getIndex(feature: String, addToFeaturizer: Boolean): Int = {
    if (!addToFeaturizer) {
      if (!featureIndexer.contains(feature)) {
        val idx = featureIndexer.getIndex(PairwiseIndexingFeaturizerJoint.UnkFeatName)
        require(idx == 0)
        idx
      } else {
        featureIndexer.getIndex(feature)
      }
    } else {
      featureIndexer.getIndex(feature)
    }
  }

  def featurizeIndex(docGraph: DocumentGraph, currMentIdx: Int, antecedentIdx: Int,
                     addToFeaturizer: Boolean): Seq[Int] = {
    featurizeIndexStandard(docGraph, currMentIdx, antecedentIdx, addToFeaturizer)
  }

  def addFeatureAndConjunctions(feats: ArrayBuffer[Int],
                                        conj: ConjType,
                                        featName: String,
                                        currMent: Mention,
                                        antecedentMent: Mention,
                                        isPairFeature: Boolean,
                                        addToFeaturizer: Boolean) {
    if (conjType == ConjType.NONE) {
      feats += getIndex(featName, addToFeaturizer)
    } else if (conjType == ConjType.TYPE || conjType == ConjType.TYPE_OR_RAW_PRON || conjType == ConjType.CANONICAL
      || conjType == ConjType.CANONICAL_OR_COMMON || conjType == ConjType.IS_NER_OR_POS) {
      val currConjunction = "&Curr=" + {
        conjType match {
          case ConjType.TYPE => currMent.computeBasicConjunctionStr
          case ConjType.TYPE_OR_RAW_PRON => currMent.computeRawPronounsConjunctionStr
          case ConjType.CANONICAL => currMent.computeCanonicalPronounsConjunctionStr
          case ConjType.IS_NER_OR_POS => if (currMent.nerString == "O") currMent.headPos else "NE"
          case _ => currMent.computeCanonicalOrCommonConjunctionStr(lexicalCounts)
        }
      }
      feats += getIndex(featName, addToFeaturizer)
      val featAndCurrConjunction = featName + currConjunction
      feats += getIndex(featAndCurrConjunction, addToFeaturizer)
      if (currMent != antecedentMent) {
        val prevConjunction = "&Prev=" + (conjType match {
          case ConjType.TYPE => antecedentMent.computeBasicConjunctionStr
          case ConjType.TYPE_OR_RAW_PRON => antecedentMent.computeRawPronounsConjunctionStr
          case ConjType.CANONICAL => antecedentMent.computeCanonicalPronounsConjunctionStr
          case ConjType.IS_NER_OR_POS => if (antecedentMent.nerString == "O") antecedentMent.headPos else "NE"
          case _ => antecedentMent.computeCanonicalOrCommonConjunctionStr(lexicalCounts)
        })
        feats += getIndex(featAndCurrConjunction + prevConjunction, addToFeaturizer)
      }
    } else if (conjType == ConjType.CANONICAL_NOPRONPRON) {
      val specialCase = currMent != antecedentMent && currMent.mentionType.isClosedClass() && antecedentMent
        .mentionType.isClosedClass()
      val currConjunction = "&Curr=" + (if (specialCase) currMent.computeBasicConjunctionStr
      else currMent
        .computeCanonicalPronounsConjunctionStr)
      feats += getIndex(featName, addToFeaturizer)
      val featAndCurrConjunction = featName + currConjunction
      feats += getIndex(featAndCurrConjunction, addToFeaturizer)
      if (currMent != antecedentMent) {
        val prevConjunction = "&Prev=" + (if (specialCase) antecedentMent.computeBasicConjunctionStr
        else
          antecedentMent.computeCanonicalPronounsConjunctionStr)
        feats += getIndex(featAndCurrConjunction + prevConjunction, addToFeaturizer)
      }
    } else if (conjType == ConjType.CANONICAL_ONLY_PAIR_CONJ) {
      feats += getIndex(featName, addToFeaturizer)
      if (isPairFeature) {
        val currConjunction = "&Curr=" + currMent.computeCanonicalPronounsConjunctionStr
        val featAndCurrConjunction = featName + currConjunction
        feats += getIndex(featAndCurrConjunction, addToFeaturizer)
        if (currMent != antecedentMent) {
          val prevConjunction = "&Prev=" + antecedentMent.computeCanonicalPronounsConjunctionStr
          feats += getIndex(featAndCurrConjunction + prevConjunction, addToFeaturizer)
        }
      }
    } else {
      throw new RuntimeException("Conjunction type not implemented")
    }
  }


  def featurizeIndexStandard(docGraph: DocumentGraph, currMentIdx: Int, antecedentIdx: Int,
                             addToFeaturizer: Boolean): Seq[Int] = {
    val currMent = docGraph.getMention(currMentIdx)
    val antecedentMent = docGraph.getMention(antecedentIdx)
    val feats = new ArrayBuffer[Int]()
    def addFeatureShortcut = (featName: String) => {
      // Only used in CANONICAL_ONLY_PAIR, so only compute the truth value in this case
      val isPairFeature = conjType == ConjType.CANONICAL_ONLY_PAIR_CONJ && !(featName.startsWith("SN") || featName
        .startsWith("PrevMent"))
      addFeatureAndConjunctions(feats, conjType, featName, currMent, antecedentMent, isPairFeature, addToFeaturizer)
    }
    // Features on anaphoricity
    val mentType = currMent.mentionType
    val startingNew = antecedentIdx == currMentIdx
    // When using very minimal feature sets, you might need to include this so every decision
    // has at least one feature over it.
    if (featsToUse.contains("+bias")) {
      addFeatureShortcut("SN=" + startingNew)
    }
    // N.B. INCLUDED IN SURFACE
    if (!featsToUse.contains("+nomentlen")) {
      //      addFeatureShortcut("SNMentLen=" + currMent.spanToString.split("\\s+").size + "-SN=" + startingNew);
      addFeatureShortcut("SNMentLen=" + currMent.words.size + "-SN=" + startingNew)
    }
    // N.B. INCLUDED IN SURFACE
    if (!featsToUse.contains("+nolexanaph") && !currMent.mentionType.isClosedClass) {
      addFeatureShortcut("SNMentHead=" + fetchHeadWordOrPos(currMent) + "-SN=" + startingNew)
      if (featsToUse.contains("+wordbackoff")) {
        val word = fetchHeadWord(antecedentMent)
        val featStart = "SNMentHead"
        //        addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Shape=" + fetchShape(word) + "-SN=" + startingNew);
        addFeatureShortcut(featStart + "Class=" + fetchClass(word) + "-SN=" + startingNew)
      }
    }
    // N.B. INCLUDED IN SURFACE
    if (!featsToUse.contains("+nolexfirstword") && !currMent.mentionType.isClosedClass) {
      addFeatureShortcut("SNMentFirst=" + fetchFirstWordOrPos(currMent) + "-SN=" + startingNew)
      if (featsToUse.contains("+wordbackoff")) {
        val word = fetchFirstWord(antecedentMent)
        val featStart = "SNMentFirst"
        //        addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Shape=" + fetchShape(word) + "-SN=" + startingNew);
        addFeatureShortcut(featStart + "Class=" + fetchClass(word) + "-SN=" + startingNew)
      }
    }
    // N.B. INCLUDED IN SURFACE
    if (!featsToUse.contains("+nolexlastword") && !currMent.mentionType.isClosedClass) {
      addFeatureShortcut("SNMentLast=" + fetchLastWordOrPos(currMent) + "-SN=" + startingNew)
      if (featsToUse.contains("+wordbackoff")) {
        val word = fetchLastWord(antecedentMent)
        val featStart = "SNMentLast"
        //        addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Shape=" + fetchShape(word) + "-SN=" + startingNew);
        addFeatureShortcut(featStart + "Class=" + fetchClass(word) + "-SN=" + startingNew)
      }
    }
    // N.B. INCLUDED IN SURFACE
    if (!featsToUse.contains("+nolexprecedingword")) {
      addFeatureShortcut("SNMentPreceding=" + fetchPrecedingWordOrPos(currMent) + "-SN=" + startingNew)
      if (featsToUse.contains("+wordbackoff")) {
        val word = fetchPrecedingWord(antecedentMent)
        val featStart = "SNMentPreceding"
        //        addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Shape=" + fetchShape(word) + "-SN=" + startingNew);
        addFeatureShortcut(featStart + "Class=" + fetchClass(word) + "-SN=" + startingNew)
      }
    }
    // N.B. INCLUDED IN SURFACE
    if (!featsToUse.contains("+nolexfollowingword")) {
      addFeatureShortcut("SNMentFollowing=" + fetchFollowingWordOrPos(currMent) + "-SN=" + startingNew)
      if (featsToUse.contains("+wordbackoff")) {
        val word = fetchFollowingWord(antecedentMent)
        val featStart = "SNMentFollowing"
        //        addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word) + "-SN=" + startingNew);
        //        addFeatureShortcut(featStart + "Shape=" + fetchShape(word) + "-SN=" + startingNew);
        addFeatureShortcut(featStart + "Class=" + fetchClass(word) + "-SN=" + startingNew)
      }
    }
    if (featsToUse.contains("+lexpenultimateword") && !currMent.mentionType.isClosedClass) {
      addFeatureShortcut("SNMentPen=" + fetchPenultimateWordOrPos(currMent) + "-SN=" + startingNew)
    }
    if (featsToUse.contains("+lexsecondword") && !currMent.mentionType.isClosedClass) {
      addFeatureShortcut("SNMentSecond=" + fetchSecondWordOrPos(currMent) + "-SN=" + startingNew)
    }
    if (featsToUse.contains("+lexprecedingby2word")) {
      addFeatureShortcut("SNMentPrecedingBy2=" + fetchPrecedingBy2WordOrPos(currMent) + "-SN=" + startingNew)
    }
    if (featsToUse.contains("+lexfollowingby2word")) {
      addFeatureShortcut("SNMentFollowingBy2=" + fetchFollowingBy2WordOrPos(currMent) + "-SN=" + startingNew)
    }
    if (featsToUse.contains("+lexgovernor")) {
      addFeatureShortcut("SNGovernor=" + fetchGovernorWordOrPos(currMent) + "-SN=" + startingNew)
    }
    // N.B. INCLUDED IN FINAL
    if (featsToUse.contains("FINAL") || featsToUse.contains("+altsyn")) {
      addFeatureShortcut("SNSynPos=" + currMent.computeSyntacticUnigram)
      addFeatureShortcut("SNSynPos=" + currMent.computeSyntacticBigram)
    }
    if (featsToUse.contains("+sentmentidx")) {
      addFeatureShortcut("SNSentMentIdx=" + computeSentMentIdx(docGraph, currMent) + "-SN=" + startingNew)
    }
    if (featsToUse.contains("+latent")) {
      for (clustererIdx <- 0 until docGraph.numClusterers) {
        addFeatureShortcut("SNTopicC" + clustererIdx + "=" + computeTopicLabel(docGraph, clustererIdx,
          currMentIdx) + "-SN=" + startingNew)
      }
    }
    if (featsToUse.contains("+def") && antecedentMent.mentionType != MentionType.PRONOMINAL) {
      addFeatureShortcut("SNDef=" + computeDefiniteness(currMent) + "-SN=" + startingNew)
    }
    if (featsToUse.contains("+synpos")) {
      addFeatureShortcut("SNSynPos=" + currMent.computeSyntacticPosition + "-SN=" + startingNew)
    }


    // Features just on the antecedent
    if (!startingNew) {
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nomentlen")) {
        //        addFeatureShortcut("PrevMentLen=" + antecedentMent.spanToString.split("\\s+").size);
        addFeatureShortcut("PrevMentLen=" + antecedentMent.words.size)
      }
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nolexanaph") && !antecedentMent.mentionType.isClosedClass) {
        addFeatureShortcut("PrevMentHead=" + fetchHeadWordOrPos(antecedentMent))
        if (featsToUse.contains("+wordbackoff")) {
          val word = fetchHeadWord(antecedentMent)
          val featStart = "PrevMentHead"
          //          addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word));
          //          addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word));
          //          addFeatureShortcut(featStart + "Shape=" + fetchShape(word));
          addFeatureShortcut(featStart + "Class=" + fetchClass(word))
        }
      }
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nolexfirstword") && !antecedentMent.mentionType.isClosedClass) {
        addFeatureShortcut("PrevMentFirst=" + fetchFirstWordOrPos(antecedentMent))
        if (featsToUse.contains("+wordbackoff")) {
          val word = fetchFirstWord(antecedentMent)
          val featStart = "PrevMentFirst"
          //          addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word));
          //          addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word));
          //          addFeatureShortcut(featStart + "Shape=" + fetchShape(word));
          addFeatureShortcut(featStart + "Class=" + fetchClass(word))
        }
      }
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nolexlastword") && !antecedentMent.mentionType.isClosedClass) {
        addFeatureShortcut("PrevMentLast=" + fetchLastWordOrPos(antecedentMent))
        if (featsToUse.contains("+wordbackoff")) {
          val word = fetchLastWord(antecedentMent)
          val featStart = "PrevMentLast"
          //          addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word));
          //          addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word));
          //          addFeatureShortcut(featStart + "Shape=" + fetchShape(word));
          addFeatureShortcut(featStart + "Class=" + fetchClass(word))
        }
      }
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nolexprecedingword")) {
        addFeatureShortcut("PrevMentPreceding=" + fetchPrecedingWordOrPos(antecedentMent))
        if (featsToUse.contains("+wordbackoff")) {
          val word = fetchPrecedingWord(antecedentMent)
          val featStart = "PrevMentPreceding"
          //          addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word));
          //          addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word));
          //          addFeatureShortcut(featStart + "Shape=" + fetchShape(word));
          addFeatureShortcut(featStart + "Class=" + fetchClass(word))
        }
      }
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nolexfollowingword")) {
        addFeatureShortcut("PrevMentFollowing=" + fetchFollowingWordOrPos(antecedentMent))
        if (featsToUse.contains("+wordbackoff")) {
          val word = fetchFollowingWord(antecedentMent)
          val featStart = "PrevMentFollowing"
          //          addFeatureShortcut(featStart + "Prefix=" + fetchPrefix(word));
          //          addFeatureShortcut(featStart + "Suffix=" + fetchSuffix(word));
          //          addFeatureShortcut(featStart + "Shape=" + fetchShape(word));
          addFeatureShortcut(featStart + "Class=" + fetchClass(word))
        }
      }
      if (featsToUse.contains("+lexpenultimateword") && !antecedentMent.mentionType.isClosedClass) {
        addFeatureShortcut("PrevMentPen=" + fetchPenultimateWordOrPos(antecedentMent))
      }
      if (featsToUse.contains("+lexsecondword") && !antecedentMent.mentionType.isClosedClass) {
        addFeatureShortcut("PrevMentSecond=" + fetchSecondWordOrPos(antecedentMent))
      }
      if (featsToUse.contains("+lexprecedingby2word")) {
        addFeatureShortcut("PrevMentPrecedingBy2=" + fetchPrecedingBy2WordOrPos(antecedentMent))
      }
      if (featsToUse.contains("+lexfollowingby2word")) {
        addFeatureShortcut("PrevMentFollowingBy2=" + fetchFollowingBy2WordOrPos(antecedentMent))
      }
      if (featsToUse.contains("+lexgovernor")) {
        addFeatureShortcut("PrevMentGovernor=" + fetchGovernorWordOrPos(antecedentMent))
      }
      // N.B. INCLUDED IN FINAL
      if (featsToUse.contains("FINAL") || featsToUse.contains("+altsyn")) {
        addFeatureShortcut("PrevSynPos=" + antecedentMent.computeSyntacticUnigram)
        addFeatureShortcut("PrevSynPos=" + antecedentMent.computeSyntacticBigram)
      }
      if (featsToUse.contains("+sentmentidx")) {
        addFeatureShortcut("PrevSentMentIdx=" + computeSentMentIdx(docGraph, antecedentMent))
      }
      if (featsToUse.contains("+latent")) {
        for (clustererIdx <- 0 until docGraph.numClusterers) {
          addFeatureShortcut("PrevTopicC" + clustererIdx + "=" + computeTopicLabel(docGraph, clustererIdx,
            antecedentIdx))
        }
      }
      if (featsToUse.contains("+def") && !antecedentMent.mentionType.isClosedClass) {
        addFeatureShortcut("PrevDef=" + computeDefiniteness(antecedentMent))
      }
      if (featsToUse.contains("+synpos")) {
        addFeatureShortcut("PrevSynPos=" + antecedentMent.computeSyntacticPosition)
      }
    }

    // Common to all pairs
    if (!startingNew) {
      // Distance to antecedent
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nomentdist")) {
        addFeatureShortcut("Dist=" + Math.min(currMentIdx - antecedentIdx, 10))
      }
      if (featsToUse.contains("+mentdistfine")) {
        val mentDist = currMentIdx - antecedentIdx
        val bucketedMentDist = if (mentDist >= 10) Math.min(mentDist / 10, 5) * 10 else mentDist
        addFeatureShortcut("Dist=" + mentDist)
      }
      // N.B. INCLUDED IN SURFACE
      if (!featsToUse.contains("+nosentdist")) {
        addFeatureShortcut("SentDist=" + Math.min(currMent.sentIdx - antecedentMent.sentIdx, 10))
      }
      // N.B. INCLUDED IN FINAL
      if (featsToUse.contains("FINAL") || featsToUse.contains("+iwi")) {
        addFeatureShortcut("iWi=" + currMent.iWi(antecedentMent))
      }
      // N.B. INCLUDED IN FINAL
      if (featsToUse.contains("FINAL") || featsToUse.contains("+altsyn")) {
        addFeatureShortcut("SynPoses=" + currMent.computeSyntacticUnigram + "-" + antecedentMent
          .computeSyntacticUnigram)
        addFeatureShortcut("SynPoses=" + currMent.computeSyntacticBigram + "-" + antecedentMent.computeSyntacticBigram)
      }
      if (featsToUse.contains("+latent")) {
        for (clustererIdx <- 0 until docGraph.numClusterers) {
          val thisLabel = computeTopicLabel(docGraph, clustererIdx, currMentIdx)
          val antLabel = computeTopicLabel(docGraph, clustererIdx, antecedentIdx)
          addFeatureShortcut("Topic=C" + clustererIdx + "-" + thisLabel)
          addFeatureShortcut("Topics=C" + clustererIdx + "-" + thisLabel + "-" + antLabel)
        }
      }
      //      if (featsToUse.contains("+synpos")) {
      //        addFeatureShortcut("SynPoses=" + currMent.computeSyntacticPosition + "-" + antecedentMent
      // .computeSyntacticPosition);
      //      }
      //      if (featsToUse.contains("+lexcontexts")) {
      //        addFeatureShortcut("CurrPrecPrevPrec=" + fetchPrecedingWordOrPos(currMent) + "-" +
      // fetchPrecedingWordOrPos(antecedentMent));
      //        addFeatureShortcut("CurrFollPrevPrec=" + fetchFollowingWordOrPos(currMent) + "-" +
      // fetchPrecedingWordOrPos(antecedentMent));
      //        addFeatureShortcut("CurrPrecPrevFoll=" + fetchPrecedingWordOrPos(currMent) + "-" +
      // fetchFollowingWordOrPos(antecedentMent));
      //        addFeatureShortcut("CurrFollPrevFoll=" + fetchFollowingWordOrPos(currMent) + "-" +
      // fetchFollowingWordOrPos(antecedentMent));
      //      }
    }
    // Closed class (mostly pronoun) specific features
    if (mentType.isClosedClass) {
      // Pronominal features
      // N.B. INCLUDED IN FINAL
      if ((featsToUse.contains("FINAL") || featsToUse.contains("+prongendnum")) && !startingNew) {
        addFeatureShortcut("AntGend=" + antecedentMent.gender)
        addFeatureShortcut("AntNumb=" + antecedentMent.number)
      }
      //      if (featsToUse.contains("+customprongendnum") && !startingNew && antecedentMent.mentionType !=
      // MentionType.PRONOMINAL) {
      //        addFeatureShortcut("AntGend=" + antecedentMent.gender + "-" + currMent.headStringLc);
      //        addFeatureShortcut("AntNumb=" + antecedentMent.number + "-" + currMent.headStringLc);
      //      }
      // N.B. INCLUDED IN FINAL
      if ((featsToUse.contains("FINAL") || featsToUse.contains("+speaker")) && !startingNew) {
        if (antecedentMent.mentionType == MentionType.PRONOMINAL) {
          addFeatureShortcut("SameSpeaker=" + (if (docGraph.corefDoc.rawDoc.isConversation) "CONVERSATION"
          else
            "ARTICLE") +
            "-" + (currMent.speaker == antecedentMent.speaker))
        }
      }
    }
    // Nominal and proper-specific features
    if (!mentType.isClosedClass) {
      if (!startingNew) {
        // Nominal and proper features
        // String match
        val exactStrMatch = (currMent.spanToString.toLowerCase.equals(antecedentMent.spanToString.toLowerCase))
        // N.B. INCLUDED IN SURFACE
        if (!featsToUse.contains("+noexactmatch")) {
          addFeatureShortcut("ExactStrMatch=" + exactStrMatch)
        }
        // N.B. INCLUDED IN FINAL
        if (featsToUse.contains("FINAL") || featsToUse.contains("+emcontained")) {
          addFeatureShortcut("ThisContained=" + (antecedentMent.spanToString.contains(currMent.spanToString)))
          addFeatureShortcut("AntContained=" + (currMent.spanToString.contains(antecedentMent.spanToString)))
        }
        // Head match
        val headMatch = currMent.headStringLc.equals(antecedentMent.headString.toLowerCase)
        // N.B. INCLUDED IN SURFACE
        if (!featsToUse.contains("+noheadmatch")) {
          addFeatureShortcut("ExactHeadMatch=" + headMatch)
        }
        if (featsToUse.contains("+lexhm")) {
          addFeatureShortcut("LexHeadMatchCurr=" + headMatch + "-" + fetchHeadWordOrPos(currMent))
          addFeatureShortcut("LexHeadMatchPrev=" + headMatch + "-" + fetchHeadWordOrPos(antecedentMent))
        }
        // N.B. INCLUDED IN FINAL
        if (featsToUse.contains("FINAL") || featsToUse.contains("+hmcontained")) {
          addFeatureShortcut("ThisHeadContained=" + (antecedentMent.spanToString.contains(currMent.headString)))
          addFeatureShortcut("AntHeadContained=" + (currMent.spanToString.contains(antecedentMent.headString)))
        }
        // Agreement
        if (featsToUse.contains("+nomgendnum")) {
          addFeatureShortcut("Gends=" + currMent.gender + "," + antecedentMent.gender)
          addFeatureShortcut("Numbs=" + currMent.number + "," + antecedentMent.number)
          addFeatureShortcut("Nerts=" + currMent.nerString + "," + antecedentMent.nerString)
        }
        if (featsToUse.contains("+bilexical")) {
          if (!antecedentMent.mentionType.isClosedClass) {
            addFeatureShortcut("Heads=" + fetchHeadWordOrPos(currMent) + "-" + fetchHeadWordOrPos(antecedentMent))
          }
        }
      }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // ADD YOUR OWN FEATURES HERE!                                                                //
    //   See above for examples of how to do this. Typically use addFeatureShortcut since this    //
    // gives you your feature as well as conjunctions, but you can also directly call             //
    // feats += getIndex(feat, addToFeaturizer);                                                  //
    //                                                                                            //
    // To control feature sets, featsToUse is passed down from pairwiseFeats (the command line    //
    // argument). We currently use magic words all starting with +, but you do have to make       //
    // sure that you don't make a magic word that's a prefix of another, or else both will be     //
    // added when the longer one is.                                                              //
    //                                                                                            //
    // Happy feature engineering!                                                                 //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // ADDITIONAL DEPENDENCY PATH FEATURE
    if (featsToUse.contains("+deppath") && !startingNew) {
      currMent.shortestDepPathTo(antecedentMent) match {
        // ignore paths over size 5 to reduce feature space
        case Some(path) if (path.size <= 5) => addFeatureShortcut("DependencyPath=" + path.mkString("->"))
        case _ =>
      }
    }


    // DISTRIBUTIONAL THESAURUS FEATURES

    def addDTFeatureShortcut(featureName: String, dtIdentifier: String, value: String,
                             isPairFeature: Boolean = true) = {
      val computedFeatureName = (new StringBuilder).append("DT").append(featureName).append(":").append(dtIdentifier)
        .append(":value=").append(value)
        .toString()
      addFeatureAndConjunctions(feats, dtConjType, computedFeatureName, currMent,
        antecedentMent, isPairFeature,
        addToFeaturizer = addToFeaturizer)
    }

    def toMention(target: MentionTarget.MentionTarget) = {
      target match {
        case MentionTarget.Current => currMent
        case MentionTarget.Antecedent => antecedentMent
      }
    }

    if (mentionPropertyComputer.thesauri != null) {
      // SINGLE FEATURES
      if (startingNew) {
        mentionPropertyComputer.thesauri.featuresToUse.filter(_.featureName.startsWith("SN")).foreach {
          case ThesaurusFeature(id, featureName, options) => val thesaurus = mentionPropertyComputer.thesauri
            .getByKey(id);
            featureName match {

              case "SNPositionInOwnContextExpansion" => val onlyClosed = options.getOrElse(ThesaurusFeature
                .OnlyClosedOption, "true").toBoolean
                if (!onlyClosed || currMent.mentionType.isClosedClass) {
                  val discretizationMethod = options.get(ThesaurusFeature.DiscretizeOption) match {
                    case Some(method) => DiscretizationMethod.withNameNoReflection(method)
                    case None => DiscretizationMethod.Boolean
                  }
                  val indexAndProb = thesaurus.positionInOwnContextExpansion(currMent)
                  val featureValue = discretizationMethod match {
                    case DiscretizationMethod.Boolean => (indexAndProb.fold(100)(_.index) <= 5).toString
                    case DiscretizationMethod.Interval => discretize(indexAndProb.fold(-1)(_.index),
                      thesaurus.maxPriorExpansionSize)
                  }
                  val sb = new mutable.StringBuilder()
                  sb.append(featureName).append("[").append(",onlyClosed=").append(onlyClosed).append("," +
                    "discretize=").append(discretizationMethod).append("]")
                  addDTFeatureShortcut(sb.toString(), id, featureValue, isPairFeature = false)
                }
              case _ => throw new IllegalArgumentException("Unknown feature " + featureName)
            }
        }
      }

      // PAIRWISE FEATURES
      if (!startingNew) {
        val antecedentOrCurrentClosed = antecedentMent.mentionType.isClosedClass || currMent.mentionType.isClosedClass

        mentionPropertyComputer.thesauri.featuresToUse.filter(!_.featureName.startsWith("SN")).foreach {
          case ThesaurusFeature(id, featureName, options) => val thesaurus = mentionPropertyComputer.thesauri
            .getByKey(id)

            def getTargetAndExpand = {
              val target = MentionTarget.withNameNoReflection(options.getOrElse(ThesaurusFeature.TargetOption,
                throw new IllegalArgumentException(featureName + " requires 'target' attribute")))
              val expand = MentionTarget.withNameNoReflection(options.getOrElse(ThesaurusFeature.ExpandOption,
                throw new IllegalArgumentException(featureName + " requires 'expand' attribute")))
              require(target != expand)
              (target, expand)
            }

            featureName match {
              case "hasExpansion" =>
                val currHasExpansion = thesaurus.priorTermExpansion(currMent).nonEmpty
                val antHasExpansion = thesaurus.priorTermExpansion(antecedentMent).nonEmpty
                addDTFeatureShortcut(featureName, id, "curr=" + currHasExpansion + "ant=" + antHasExpansion)

              case "priorExpansion" =>
                val (target, expand) = getTargetAndExpand
                val onlyOpen = options.getOrElse(ThesaurusFeature.OnlyOpenOption, "true").toBoolean
                val discretizationMethod = options.get(ThesaurusFeature.DiscretizeOption) match {
                  case Some(method) => DiscretizationMethod.withNameNoReflection(method)
                  case None => DiscretizationMethod.Boolean
                }

                if (!onlyOpen || !antecedentOrCurrentClosed) {
                  val index = thesaurus.positionInPriorExpansion(toMention(target), toMention(expand))
                  val featureValue = discretizationMethod match {
                    case DiscretizationMethod.Boolean => (index > -1).toString
                    case DiscretizationMethod.IntervalFile => chiMergeDiscretize(index, featureName)
                    case DiscretizationMethod.Interval => discretize(index, thesaurus.maxPriorExpansionSize)
                  }

                  val sb = new mutable.StringBuilder()
                  sb.append(featureName).append("[").append("target=").append(target).append("," +
                    "expand=").append(expand).append(",onlyOpen=").append(onlyOpen).append("," +
                    "discretize=").append(discretizationMethod).append("]")
                  addDTFeatureShortcut(sb.toString(), id, featureValue)
                }

              case "incompatibleHeads" =>
                val isIncompatible = thesaurus.getAntonymCount(currMent, antecedentMent) > dtRemoveIncompatibleTermsK

                addDTFeatureShortcut(featureName, id, isIncompatible.toString)

              case "incompatibleProperty" =>
                val result = thesaurus.incompatibleAttributes(currMent, antecedentMent)
                if (result == AttributeIncompatibilityResult.Incompatible) addDTFeatureShortcut(featureName, id,
                  "incompatible")
                else if (result == AttributeIncompatibilityResult.Unknown) addDTFeatureShortcut(featureName, id,
                  "probably-compatible")

              case "rerankedExpansion" =>
                val (target, expand) = getTargetAndExpand
                val onlyOpen = options.getOrElse(ThesaurusFeature.OnlyOpenOption, "true").toBoolean
                val usePartnerContext = options.getOrElse(ThesaurusFeature.ContextOption, "false").toBoolean
                val filterOption = options.getOrElse(ThesaurusFeature.FilterOption, "true").toBoolean
                val discretizationMethod = options.get(ThesaurusFeature.DiscretizeOption) match {
                  case Some(method) => DiscretizationMethod.withNameNoReflection(method)
                  case None => DiscretizationMethod.Boolean
                }

                if (!onlyOpen || !antecedentOrCurrentClosed) {
                  val index = thesaurus.positionInRerankedPriorExpansion(toMention(target), toMention(expand),
                    usePartnerContext, filterOption)
                  val featureValue = discretizationMethod match {
                    case DiscretizationMethod.Boolean => (index > -1).toString
                    case DiscretizationMethod.Interval => discretize(index, thesaurus.maxPriorExpansionSize)
                  }

                  val sb = new mutable.StringBuilder()
                  sb.append(featureName).append("[").append("target=").append(target).append("," +
                    "expand=").append(expand).append(",onlyOpen=").append(onlyOpen).append(",usePartnerContext=").append(
                      usePartnerContext).append("filterBims=").append(filterOption).append("," +
                    "discretize=").append(discretizationMethod).append("]")
                  addDTFeatureShortcut(sb.toString(), id, featureValue)
                }

              case "inContextExpansion" =>
                val (target, expand) = getTargetAndExpand
                val discretizationMethod = options.get(ThesaurusFeature.DiscretizeOption) match {
                  case Some(method) => DiscretizationMethod.withNameNoReflection(method)
                  case None => DiscretizationMethod.Boolean
                }
                val onlyClosed = options.getOrElse(ThesaurusFeature.OnlyClosedOption, "true").toBoolean

                // if onlyClosed, only do this if the expansion target is closed (otherwise,
                // expansion would not be needed)
                if (!onlyClosed || toMention(expand).mentionType.isClosedClass) {
                  val index = thesaurus.positionInContextExpansion(toMention(target), toMention(expand))
                  val featureValue = index match {
                    case Some(idx) => discretizationMethod match {
                      case DiscretizationMethod.Boolean => (idx > -1).toString
                      case DiscretizationMethod.Interval => discretize(idx, thesaurus.maxPriorExpansionSize)
                      case DiscretizationMethod.IntervalFile => chiMergeDiscretize(idx, featureName)
                    }
                    case None => "EMPTY_CONTEXT_EXPANSION"
                  }

                  val sb = new mutable.StringBuilder()
                  sb.append(featureName).append("[").append("target=").append(target).append("," +
                    "expand=").append(expand).append(",discretize=").append(discretizationMethod).append("," +
                    "onlyClosed=").append(onlyClosed).append("]")
                  val theName = sb.toString()

                  addDTFeatureShortcut(theName, id, featureValue)

                  // add lexicalized Bim features (to capture for instance that "say" is not really a strong indicator)
                  /* val bimSet = toMention(expand).bimCache(id)
                   if (lexicalCounts.bimCounts.containsKey(bimSet)) {
                     addDTFeatureShortcut(theName + "ExpandedBims", id, bimSet.toSeq.sorted.mkString("-"))
                   }*/
                }

              case "inArc2ContextExpansion" =>
                val (target, expand) = getTargetAndExpand
                val onlyClosed = options.getOrElse(ThesaurusFeature.OnlyClosedOption, "true").toBoolean
                val limit = options.getOrElse(ThesaurusFeature.LimitOption, "10").toInt

                val discretizationMethod = options.get(ThesaurusFeature.DiscretizeOption) match {
                  case Some(method) => DiscretizationMethod.withNameNoReflection(method)
                  case None => DiscretizationMethod.Boolean
                }

                // if onlyClosed, only do this if the expansion target is closed (otherwise,
                // expansion would not be needed)
                if (!onlyClosed || toMention(expand).mentionType.isClosedClass) {
                  val theName = new mutable.StringBuilder().append(featureName).append("[").append("target=").
                    append(target).append(",expand=").append(expand).append(",onlyClosed=").append(onlyClosed).
                    append(",limit=").append(limit).append("discretize=").append(discretizationMethod).append("]").toString()

                  val result = thesaurus.positionInArc2ContextExpansion(toMention(target), toMention(expand), limit)

                  result match {
                    case Some(idx) =>
                      val featureValue = discretizationMethod match {
                        case DiscretizationMethod.Boolean => (idx > -1).toString
                        case DiscretizationMethod.Interval => discretize(idx, thesaurus.maxPriorExpansionSize)
                      }
                      addDTFeatureShortcut(theName, id, featureValue)

                    case None => addDTFeatureShortcut(theName, id, "EMPTY_CONTEXT_EXPANSION")
                  }
                }


              case "sharedPriorExpansionCount" =>
                val onlyOpen = options.getOrElse(ThesaurusFeature.OnlyOpenOption, "false").toBoolean
                if (!onlyOpen || !antecedentOrCurrentClosed) {
                  val sharedCount = thesaurus.sharedPriorExpansionCount(antecedentMent, currMent)
                  if (sharedCount.isDefined) {
                    val value = discretizePercentage(sharedCount.get)
                    addDTFeatureShortcut(featureName, id, value)
                  }
                }

              case "propertiesInPriorExpansion" =>
                val (target, expand) = getTargetAndExpand
                val discretizationMethod = options.get(ThesaurusFeature.DiscretizeOption) match {
                  case Some(method) => DiscretizationMethod.withNameNoReflection(method)
                  case None => DiscretizationMethod.Boolean
                }

                val index = thesaurus.attributesInPriorExpansion(toMention(target), toMention(expand))

                val featureValue = if (index.isDefined) discretizationMethod match {
                  case DiscretizationMethod.Boolean => (index.get > -1).toString
                  case DiscretizationMethod.Interval => discretize(index.get, thesaurus.maxPriorExpansionSize)
                } else "emptyExpansion"
                val sb = new mutable.StringBuilder()
                sb.append(featureName).append("[").append("target=").append(target).append("," +
                  "expand=").append(expand).append(",discretize=").append(discretizationMethod).append("]")

                addDTFeatureShortcut(sb.toString(), id, featureValue)

              case "hasIsas" =>
                val curHasIsas = thesaurus.isaSets(currMent).nonEmpty
                val antHasIsas = thesaurus.isaSets(antecedentMent).nonEmpty
                addDTFeatureShortcut(featureName, id, "curr=" + curHasIsas + "ant=" + antHasIsas)

              case "headsSharedIsas" =>
                val lexicalize = options.getOrElse(ThesaurusFeature.lexicalizeOption, "true").toBoolean
                val sharedIsas = thesaurus.headsSharedIsas(currMent, antecedentMent)
                if (sharedIsas.isEmpty) {
                  //addDTFeatureShortcut(featureName, id, "NOEXPAND")
                } else if (sharedIsas.get._2.size == 0) {
                  addDTFeatureShortcut(featureName, id, "NONE")
                }
                else {
                  addDTFeatureShortcut(featureName, id, discretizePercentage(sharedIsas.get._1))
                  if (lexicalize) addDTFeatureShortcut(featureName, id, "TRUE&top=" + sharedIsas.get._2(0))
                }

              case "isIsa" =>
                // looking up IS-As only makes sense for common and proper nouns
                if (!antecedentOrCurrentClosed) {
                  val (target, expand) = getTargetAndExpand

                  val result = thesaurus.headIsIsa(toMention(expand), toMention(target))


                  val sb = new mutable.StringBuilder()
                  sb.append(featureName).append("[").append("target=").append(target).append("," +
                    "expand=").append(expand).append("]")
                  if (result.isDefined) {
                    if (result.get) {
                      addDTFeatureShortcut(sb.toString(), id, "TRUE")
                      //addDTFeatureShortcut(sb.toString(), id, "TRUE&headIsa=" + thesaurus
                      // .mentionHeadToIsaRepresentation(toMention(target)))

                    } else {
                      addDTFeatureShortcut(sb.toString(), id, "FALSE&joMatch=" + (thesaurus.getTerm(toMention(expand))
                        == thesaurus.getTerm(toMention(target))))
                    }
                  } else {
                    //addDTFeatureShortcut(sb.toString(), id, "NOEXPAND")
                  }
                }

              case "isPropertyIsa" =>
                val (target, expand) = getTargetAndExpand
                val lexicalize = options.getOrElse(ThesaurusFeature.lexicalizeOption, "true").toBoolean

                val result = thesaurus.mentionIsAttributeIsa(toMention(expand), toMention(target))
                result match {
                  case Some(matchFound) => val name = new mutable.StringBuilder().append(featureName).append("[")
                    .append("target=").append(target).append("," +
                    "expand=").append(expand).append("]").toString()
                    val featureValue = matchFound.toString
                    addDTFeatureShortcut(name, id, featureValue)

                    // add lexicalized ISA
                    if (lexicalize && matchFound) addDTFeatureShortcut(name, id, featureValue + "&isaHead=" + thesaurus
                      .termToIsaRepresentation(toMention(target)))

                  case None => // ignore mentions without properties
                }

              case _ => throw new IllegalArgumentException(s"Unknown feature $featureName for thesaurus $id")
            }
        }
      }
    }

    bkFeaturizer.featurizeIndexStandard(docGraph, currMent, antecedentMent, startingNew, addFeatureShortcut)

    feats.toArray
  }

  private val chimergeIntervalsCache = mutable.Map.empty[String, Traversable[Interval]]

  private var _prop: java.util.Properties = null

  private def prop = {
    if (_prop == null) {
      _prop = new java.util.Properties
      val reader = new FileReader(chimergeIntervalsFile)
      _prop.load(reader)
      reader.close()
    }
    _prop
  }

  private def getChimergeIntervals(key: String) = {
    chimergeIntervalsCache.getOrElseUpdate(key, {
      val values = prop.getProperty(key).trim.split(";").map(_.toInt)
      val intervals = ArrayBuffer.empty[Interval]
      for (i <- 0 until values.size) {
        if (i == 0) {
          // left open
          intervals += new OpenInterval(values(i), OpenInterval.OpenSide.Left)
        } else if (i == values.size - 1) {
          // right open
          intervals += new OpenInterval(values(i), OpenInterval.OpenSide.Right)
        } else {
          // range interval
          intervals += new RangeInterval(values(i), values(i + 1))
        }
      }
      intervals.toArray.toTraversable
    })
  }

  private def chiMergeDiscretize(value: Int, chiMergeKey: String): String = {
    val myInterval = getChimergeIntervals(chiMergeKey).find(_.contains(value))
    myInterval match {
      case Some(interval) => interval.toString
      case None => throw new IllegalStateException("value " + value + " exceeds ChiMerge intervals though all are " +
        "open ended")
    }
  }

  /**
   * TODO doc
   */
  private def discretize(value: Int, maxValue: Int): String = if (discretizeIntervalFactor <=
    0) value
    .toString
  else discretize(value, maxValue, (maxValue * discretizeIntervalFactor).toInt)

  /**
   * TODO doc
   */
  private def discretize(value: Int, maxValue: Int, step: Int, spellOutUntil: Int = 20):
  String = {
    if (value < 0) "-" + discretize(-value, maxValue, step)
    else if
    (value >= maxValue) maxValue + "+"
    else if (value <= spellOutUntil) value.toString
    else {
      val fraction = value / step
      val first = if (fraction > 0) fraction * step else spellOutUntil + 1
      val second = ((fraction + 1) * step) - 1
      first + "-" + second
    }
  }

  /**
   * Converts a continuous percentage value to a discrete value from a finite set by rounding it to a decimal place.
   *
   * @param value the value to make discrete in the Interval [0,1]
   */
  private def discretizePercentage(value: Double, roundFactor: Int = 10): String = {
    val rounded = Math.round(value * roundFactor)
    if (rounded < roundFactor) "0." + rounded.toInt.toString else "1.0"
  }

  private def fetchHeadWordOrPos(ment: Mention) = fetchWordOrPosDefault(ment.headStringLc,
    ment.pos(ment.headIdx - ment.startIdx), lexicalCounts.commonHeadWordCounts)

  private def fetchFirstWordOrPos(ment: Mention) = fetchWordOrPosDefault(ment.words(0).toLowerCase,
    ment.pos(0),
    lexicalCounts.commonFirstWordCounts)

  private def fetchLastWordOrPos(ment: Mention) = {
    if (ment.words.size == 1 || ment.endIdx - 1 == ment.headIdx) {
      ""
    } else {
      fetchWordOrPosDefault(ment.words(ment.words.size - 1).toLowerCase, ment.pos(ment.pos.size - 1),
        lexicalCounts.commonLastWordCounts)
    }
  }

  private def fetchPenultimateWordOrPos(ment: Mention) = {
    if (ment.words.size <= 2) {
      ""
    } else {
      fetchWordOrPosDefault(ment.words(ment.words.size - 2).toLowerCase, ment.pos(ment.pos.size - 2),
        lexicalCounts.commonPenultimateWordCounts)
    }
  }

  private def fetchSecondWordOrPos(ment: Mention) = {
    if (ment.words.size <= 3) {
      ""
    } else {
      fetchWordOrPosDefault(ment.words(1).toLowerCase, ment.pos(1), lexicalCounts.commonSecondWordCounts)
    }
  }

  private def fetchPrecedingWordOrPos(ment: Mention) = fetchWordOrPosDefault(ment
    .contextWordOrPlaceholder(-1)
    .toLowerCase, ment.contextPosOrPlaceholder(-1), lexicalCounts.commonPrecedingWordCounts)

  private def fetchFollowingWordOrPos(ment: Mention) = fetchWordOrPosDefault(ment
    .contextWordOrPlaceholder(ment.words
    .size).toLowerCase, ment.contextPosOrPlaceholder(ment.words.size),
    lexicalCounts.commonFollowingWordCounts)

  private def fetchPrecedingBy2WordOrPos(ment: Mention) = fetchWordOrPosDefault(ment
    .contextWordOrPlaceholder(-2)
    .toLowerCase, ment.contextPosOrPlaceholder(-2), lexicalCounts.commonPrecedingBy2WordCounts)

  private def fetchFollowingBy2WordOrPos(ment: Mention) = fetchWordOrPosDefault(ment
    .contextWordOrPlaceholder(ment
    .words.size + 1).toLowerCase, ment.contextPosOrPlaceholder(ment.words.size + 1),
    lexicalCounts.commonFollowingBy2WordCounts)

  private def fetchGovernorWordOrPos(ment: Mention) = fetchWordOrPosDefault(ment.governor.toLowerCase,
    ment.governorPos, lexicalCounts.commonGovernorWordCounts)


  private def fetchWordOrPosDefault(word: String, pos: String, counter: Counter[String]) = {
    if (counter.containsKey(word)) {
      word
    } else if (featsToUse.contains("+NOPOSBACKOFF")) {
      ""
    } else {
      pos
    }
  }

  private def fetchPrefix(word: String) = {
    if (word.size >= 3 && lexicalCounts.commonPrefixCounts.containsKey(word.substring(0, 3))) {
      word.substring(0, 3)
    } else if (word.size >= 2 && lexicalCounts.commonPrefixCounts.containsKey(word.substring(0, 2))) {
      word.substring(0, 2)
    } else if (lexicalCounts.commonPrefixCounts.containsKey(word.substring(0, 1))) {
      word.substring(0, 1)
    } else {
      ""
    }
  }

  private def fetchSuffix(word: String) = {
    if (word.size >= 3 && lexicalCounts.commonSuffixCounts.containsKey(word.substring(word.size - 3))) {
      word.substring(word.size - 3)
    } else if (word.size >= 2 && lexicalCounts.commonSuffixCounts.containsKey(word.substring(word.size
      - 2))) {
      word.substring(word.size - 2)
    } else if (lexicalCounts.commonSuffixCounts.containsKey(word.substring(word.size - 1))) {
      word.substring(word.size - 1)
    } else {
      ""
    }
  }

  private def fetchShape(word: String) = {
    if (lexicalCounts.commonShapeCounts.containsKey(NerExample.shapeFor(word))) {
      NerExample.shapeFor(word)
    } else {
      ""
    }
  }

  private def fetchClass(word: String) = {
    if (lexicalCounts.commonClassCounts.containsKey(NerExample.classFor(word))) {
      NerExample.classFor(word)
    } else {
      ""
    }
  }

  private def fetchHeadWord(ment: Mention) = ment.words(ment.headIdx - ment.startIdx)

  private def fetchFirstWord(ment: Mention) = ment.words(0)

  private def fetchLastWord(ment: Mention) = ment.words(ment.pos.size - 1)

  private def fetchPrecedingWord(ment: Mention) = ment.contextWordOrPlaceholder(-1)

  private def fetchFollowingWord(ment: Mention) = ment.contextWordOrPlaceholder(ment.pos.size)

  //
  //  private def fetchHeadPos(ment: Mention) = ment.pos(ment.headIdx - ment.startIdx);
  //  private def fetchFirstPos(ment: Mention) = ment.pos(0);
  //  private def fetchLastPos(ment: Mention) = ment.pos(ment.pos.size - 1);
  //  private def fetchPrecedingPos(ment: Mention) = ment.contextPosOrPlaceholder(-1);
  //  private def fetchFollowingPos(ment: Mention) = ment.contextPosOrPlaceholder(ment.pos.size);

  // TODO: when porting to other languages check a dictionary instead of using string constants
  private def computeDefiniteness(ment: Mention) = {
    val firstWord = ment.words(0).toLowerCase
    if (firstWord.equals("the")) {
      "DEF"
    } else if (firstWord.equals("a") || firstWord.equals("an")) {
      "INDEF"
    } else {
      "NONE"
    }
  }

  private def computePronNumber(ment: Mention) = {
    val firstWord = ment.words(0).toLowerCase
    if (PronounDictionary.singularPronouns.contains(ment.headStringLc)) {
      "SING"
    } else if (PronounDictionary.pluralPronouns.contains(ment.headStringLc)) {
      "PLU"
    } else {
      "UNKNOWN"
    }
  }

  private def computePronGender(ment: Mention) = {
    val firstWord = ment.words(0).toLowerCase
    if (PronounDictionary.malePronouns.contains(ment.headStringLc)) {
      "MALE"
    } else if (PronounDictionary.femalePronouns.contains(ment.headStringLc)) {
      "FEMALE"
    } else if (PronounDictionary.neutralPronouns.contains(ment.headStringLc)) {
      "NEUTRAL"
    } else {
      "UNKNOWN"
    }
  }

  private def computePronPerson(ment: Mention) = {
    val firstWord = ment.words(0).toLowerCase
    if (PronounDictionary.firstPersonPronouns.contains(ment.headStringLc)) {
      "1st"
    } else if (PronounDictionary.secondPersonPronouns.contains(ment.headStringLc)) {
      "2nd"
    } else if (PronounDictionary.firstPersonPronouns.contains(ment.headStringLc)) {
      "3rd"
    } else {
      "OTHER"
    }
  }

  private def computeSentMentIdx(docGraph: DocumentGraph, ment: Mention) = {
    var currIdx = ment.mentIdx - 1
    while (currIdx >= 0 && docGraph.getMention(currIdx).sentIdx == ment.sentIdx) {
      currIdx -= 1
    }
    ment.mentIdx - currIdx + 1
  }

  def computeTopicLabel(docGraph: DocumentGraph, clustererIdx: Int, mentIdx: Int): String = {
    val ment = docGraph.getMention(mentIdx)
    if (ment.mentionType == MentionType.PRONOMINAL && featsToUse.contains("noprons")) {
      "PRON"
    } else if ((ment.mentionType == MentionType.NOMINAL || ment.mentionType == MentionType.PROPER) && featsToUse
      .contains("nonomsprops")) {
      "NOMPROP"
    } else {
      docGraph.getBestCluster(clustererIdx, mentIdx) + ""
    }
  }

  def computeDistribLabel(docGraph: DocumentGraph, clustererIdx: Int, mentIdx: Int, valIdx: Int): Int = {
    docGraph.storedDistributedLabels(clustererIdx)(mentIdx)(valIdx)
  }

  def numDistribLabels(docGraph: DocumentGraph, clustererIdx: Int): Int = {
    docGraph.numClusters(clustererIdx)
  }

  // SERIALIZATION
  @throws(classOf[IOException])
  private def writeObject(out: ObjectOutputStream): Unit = {
    _prop = null
    out.defaultWriteObject()
  }

  override def getPairwiseFeatsEnabled: String = featsToUse
}

object PairwiseIndexingFeaturizerJoint {
  val UnkFeatName = "UNK_FEAT"
}
