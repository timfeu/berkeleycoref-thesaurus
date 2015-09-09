package edu.berkeley.nlp.coref

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.stanford.nlp.trees.{TreeGraphNode, TypedDependency}
import org.jobimtext.coref.berkeley.DistributionalThesaurusComputer

class Mention(val rawDoc: BaseDoc,
              val mentIdx: Int,
              val sentIdx: Int,
              val startIdx: Int,
              val endIdx: Int,
              val headIdx: Int,
              val mentionType: MentionType,
              val nerString: String,
              val number: Number,
              val gender: Gender) {


  // Cache these computations since they happen for every feature...
  //  val cachedRawPronConjStr = if (mentionType == MentionType.PRONOMINAL) headStringLc else mentionType.toString;
  private val cachedRawPronConjStr = if (mentionType.isClosedClass) headStringLc else mentionType.toString;
  //  val cachedCanonicalPronConjStr = if (mentionType == MentionType.PRONOMINAL) {
  private val cachedCanonicalPronConjStr = if (mentionType.isClosedClass) {
    if (!PronounDictionary.canonicalize(headStringLc).equals("")) {
      PronounDictionary.canonicalize(headStringLc);
    } else {
      headStringLc;
    }
  } else {
    mentionType.toString();
  }
  private var cachedCanonicalOrCommonConjStr = "";

  def speaker = rawDoc.speakers(sentIdx)(headIdx);

  /**
   * @return the mention's head string.
   */
  def headString = rawDoc.words(sentIdx)(headIdx);

  /**
   * @return the mention's head string, lowercase.
   */
  def headStringLc = rawDoc.words(sentIdx)(headIdx).toLowerCase;

  def headStringLemma = rawDoc.lemmas(sentIdx)(headIdx).toLowerCase

  def words = rawDoc.words(sentIdx).slice(startIdx, endIdx);

  def pos = rawDoc.pos(sentIdx).slice(startIdx, endIdx);

  def spanToString = rawDoc.words(sentIdx).slice(startIdx, endIdx).reduce(_ + " " + _);

  /**
   * Returns the word from the same sentence as the mention at the specified index or the default placeholders if
   * there is no word at that index.
   *
   * @param idx the word's position in the sentence, 0 being the sentence's first word
   *
   * @return the word at the index or one of the placeholders [[Mention.StartPosPlaceholder]],
   *         [[Mention.EndWordPlaceholder]]
   */
  def accessWordOrPlaceholder(idx: Int): String = accessWordOrPlaceholder(idx, Mention.StartWordPlaceholder,
    Mention.EndWordPlaceholder)

  /**
   * Returns the word from the same sentence as the mention at the specified index or the specified placeholder if
   * there is no word at that index.
   *
   * @param idx the word's position in the sentence, 0 being the sentence's first word
   * @param placeholder the string to use if the index is outside the sentence's boundary
   *
   * @return the word at the index or the placeholder
   */
  def accessWordOrPlaceholder(idx: Int, placeholder: String): String = accessWordOrPlaceholder(idx, placeholder,
    placeholder)

  /**
   * Returns the word from the same sentence as the mention at the specified index or the specified placeholders if
   * there is no word at that index.
   *
   * @param idx the word's position in the sentence, 0 being the sentence's first word
   * @param startPlaceholder the string to use if the index is < 0
   * @param endPlaceholder the string to use if the index exceeds the sentence's size
   *
   * @return the word at the index or the appropriate placeholder
   */
  def accessWordOrPlaceholder(idx: Int, startPlaceholder: String, endPlaceholder: String): String = {
    if (idx < 0) startPlaceholder
    else if (idx >= rawDoc.words(sentIdx).size) endPlaceholder
    else rawDoc.words(sentIdx)(idx)
  }

  def accessPosOrPlaceholder(idx: Int): String = {
    if (idx < 0) Mention.StartPosPlaceholder
    else if (idx >= rawDoc.pos(sentIdx).size) Mention.EndPosPlaceholder
    else
      rawDoc.pos(sentIdx)(idx)
  }

  def contextWordOrPlaceholder(idx: Int) = accessWordOrPlaceholder(startIdx + idx);

  def contextPosOrPlaceholder(idx: Int) = accessPosOrPlaceholder(startIdx + idx);

  def headPos = accessPosOrPlaceholder(headIdx)

  def governor = governorHelper(false);

  def governorPos = governorHelper(true);

  private def governorHelper(pos: Boolean) = {
    val parentIdx = rawDoc.trees(sentIdx).childParentDepMap(headIdx);
    if (parentIdx == -1) {
      "[ROOT]"
    } else {
      (if (headIdx < parentIdx) "L" else "R") + "-" + (if (pos) rawDoc.pos(sentIdx)(parentIdx)
      else rawDoc.words
        (sentIdx)(parentIdx));
    }
  }

  //  private def wordsFromBaseIndexAndOffset(baseIdx: Int, offsets: Seq[Int]) = offsets.map(offset =>
  // accessWordOrPlaceholder(baseIdx + offset)).reduce(_ + " " + _)
  //  private def possFromBaseIndexAndOffset(baseIdx: Int, offsets: Seq[Int]) = offsets.map(offset =>
  // accessPosOrPlaceholder(baseIdx + offset)).reduce(_ + " " + _)
  //
  //  def wordsFromStart(offsets: Seq[Int]) = wordsFromBaseIndexAndOffset(startIdx, offsets);
  //  def wordsFromHead(offsets: Seq[Int]) = wordsFromBaseIndexAndOffset(headIdx, offsets);
  //  def wordsFromEnd(offsets: Seq[Int]) = wordsFromBaseIndexAndOffset(endIdx, offsets);
  //  def possFromStart(offsets: Seq[Int]) = possFromBaseIndexAndOffset(startIdx, offsets);
  //  def possFromHead(offsets: Seq[Int]) = possFromBaseIndexAndOffset(headIdx, offsets);
  //  def possFromEnd(offsets: Seq[Int]) = possFromBaseIndexAndOffset(endIdx, offsets);
  //
  //  def wordFromStart(offset: Int) = accessWordOrPlaceholder(startIdx + offset);
  //  def wordFromHead(offset: Int) = accessWordOrPlaceholder(headIdx + offset);
  //  def wordFromEnd(offset: Int) = accessWordOrPlaceholder(endIdx + offset);
  //  def posFromStart(offset: Int) = accessPosOrPlaceholder(startIdx + offset);
  //  def posFromHead(offset: Int) = accessPosOrPlaceholder(headIdx + offset);
  //  def posFromEnd(offset: Int) = accessPosOrPlaceholder(endIdx + offset);

  // These are explicit rather than in terms of Seq[Int] for lower overhead during
  // feature computation.
  //  def wordBigramFromStart(offset1: Int, offset2: Int) = accessWordOrPlaceholder(startIdx + offset1) + " " +
  // accessWordOrPlaceholder(startIdx + offset2);
  //  def wordBigramFromHead(offset1: Int, offset2: Int) = accessWordOrPlaceholder(headIdx + offset1) + " " +
  // accessWordOrPlaceholder(headIdx + offset2);
  //  def wordBigramFromEnd(offset1: Int, offset2: Int) = accessWordOrPlaceholder(endIdx + offset1) + " " +
  // accessWordOrPlaceholder(endIdx + offset2);
  //  def posBigramFromStart(offset1: Int, offset2: Int) = accessPosOrPlaceholder(startIdx + offset1) + " " +
  // accessPosOrPlaceholder(startIdx + offset2);
  //  def posBigramFromHead(offset1: Int, offset2: Int) = accessPosOrPlaceholder(headIdx + offset1) + " " +
  // accessPosOrPlaceholder(headIdx + offset2);
  //  def posBigramFromEnd(offset1: Int, offset2: Int) = accessPosOrPlaceholder(endIdx + offset1) + " " +
  // accessPosOrPlaceholder(endIdx + offset2);

  def computeBasicConjunctionStr = mentionType.toString;

  def computeRawPronounsConjunctionStr = cachedRawPronConjStr;

  def computeCanonicalPronounsConjunctionStr = cachedCanonicalPronConjStr;

  def computeCanonicalOrCommonConjunctionStr(lexicalCountsBundle: LexicalCountsBundle) = {
    if (cachedCanonicalOrCommonConjStr == "") {
      cachedCanonicalOrCommonConjStr = if (mentionType.isClosedClass || lexicalCountsBundle.commonHeadWordCounts
        .getCount(headStringLc) < 500) {
        cachedCanonicalPronConjStr
      } else {
        headStringLc;
      }
    }
    cachedCanonicalOrCommonConjStr;
  }

  def iWi(other: Mention) = {
    sentIdx == other.sentIdx && ((other.startIdx <= this.startIdx && this.endIdx <= other.endIdx) ||
      (this.startIdx <= other.startIdx && other.endIdx <= this.endIdx));
  }

  def computeSyntacticUnigram: String = rawDoc.trees(sentIdx).computeSyntacticUnigram(headIdx);

  def computeSyntacticBigram: String = rawDoc.trees(sentIdx).computeSyntacticBigram(headIdx);

  def computeSyntacticPosition: String = rawDoc.trees(sentIdx).computeSyntacticPositionSimple(headIdx);

  // THESAURUS values
  /**
   * Mapping from [[DistributionalThesaurusComputer]] identifiers to the cached '''term''' ("word"
   * or "filling") representing this mention in the respective thesaurus.
   */
  var termCache: Map[String, String] = Map.empty[String, String]

  /**
   * Mapping from [[DistributionalThesaurusComputer]] identifiers to the cached '''context feautures'''
   * ("patterns") of this mention, specific to the respective thesaurus.
   */
  var bimCache: Map[String, Set[String]] = Map.empty[String, Set[String]]

  override def toString: String = words.mkString(" ")

  /**
   * Calculates the shortest dependency path from the head of this mention to the target's head, given that they are from
   * the same sentence.
   *
   * @return None if both are not in the same sentence, Some(Seq(dependency label)) otherwise
   */
  def shortestDepPathTo(other: Mention): Option[List[String]] = {
    class InfInt(val value: Option[Int]) extends Ordered[InfInt] {
      def isInfinite = !value.isDefined
      def +(other: InfInt) = {
        if (this.isInfinite || other.isInfinite) {
          new InfInt(None)
        } else {
          new InfInt(Some(this.value.get + other.value.get))
        }
      }

      def +(other: Int) = {
        if (this.isInfinite) {
          new InfInt(None)
        } else {
          new InfInt(Some(value.get + other))
        }
      }

      override def <(other: InfInt) = {
        !this.isInfinite && (other.isInfinite || this.value.get < other.value.get)
      }

      override def equals(obj: scala.Any): Boolean = {
        if (!obj.isInstanceOf[InfInt]) false else this.value == obj.asInstanceOf[InfInt].value
      }

      override def compare(that: InfInt): Int = if (this < that) -1 else if (this eq that) 0 else 1
    }

    if (sentIdx != other.sentIdx) None
    else {
      val sentenceDependencies = rawDoc.stanfordDependencies(sentIdx)

      def getNode(ment: Mention) = {
        val idx = ment.headIdx
        sentenceDependencies.collectFirst({
          case dep if dep.dep().index() - 1 == idx => dep.dep()
          case dep if dep.gov().index() - 1 == idx => dep.gov()
        })
      }

      val myNode = getNode(this)
      val otherNode = getNode(other)

      if (myNode.isEmpty || otherNode.isEmpty) None else {
        val allNodes = sentenceDependencies.foldLeft(Set.empty[TreeGraphNode])((set, dep) => set + dep.dep() + dep.gov())
        val neighborsMap = scala.collection.mutable.Map.empty[TreeGraphNode, Set[(TypedDependency, TreeGraphNode)]]

        for (dep <- sentenceDependencies) {
          neighborsMap(dep.dep()) = neighborsMap.getOrElse(dep.dep(), Set[(TypedDependency, TreeGraphNode)]()) + ((dep, dep.gov()))
          neighborsMap(dep.gov()) = neighborsMap.getOrElse(dep.gov(), Set[(TypedDependency, TreeGraphNode)]()) + ((dep, dep.dep()))
        }

        val queue = scala.collection.mutable.Set.empty[TreeGraphNode]

        val dist = scala.collection.mutable.Map.empty[TreeGraphNode, InfInt]
        dist(myNode.get) = new InfInt(Some(0))

        val previous = scala.collection.mutable.Map.empty[TreeGraphNode, Option[(String, TreeGraphNode)]]

        for (node <- allNodes) {
          if (node != myNode.get) {
            dist(node) = new InfInt(None)
          }
          queue.add(node)
        }

          while (queue.nonEmpty) {
            val u = queue.minBy(dist(_))
            queue.remove(u)

            if (u == otherNode.get) {
              queue.clear()
            } else {
              for (neighbor <- neighborsMap(u)) {
                val alt = dist(u) + 1
                if (alt < dist(neighbor._2)) {
                  dist(neighbor._2) = alt

                  val depString = (if (neighbor._1.gov() == neighbor._2) "-" else "") + neighbor._1.reln().toString.toLowerCase

                  previous(neighbor._2) = Some(depString, u)
                }
              }
            }
          }

        // build path
        var path = List[String]()
        var u = otherNode.get

        while (previous.contains(u) && previous(u).isDefined) {
          path = previous(u).get._1 :: path
          u = previous(u).get._2
        }

        Some(path)
      }
    }
  }
}

object Mention {

  val StartWordPlaceholder = "<s>";
  val EndWordPlaceholder = "</s>";
  val StartPosPlaceholder = "<S>";
  val EndPosPlaceholder = "</S>";

  def createMentionComputeProperties(rawDoc: BaseDoc,
                                     mentIdx: Int,
                                     sentIdx: Int,
                                     startIdx: Int,
                                     endIdx: Int,
                                     headIdx: Int,
                                     propertyComputer: MentionPropertyComputer,
                                     config: CorefSystemConfiguration): Mention = {
    // NER
    var nerString = "O";
    for (chunk <- rawDoc.nerChunks(sentIdx)) {
      if (chunk.start <= headIdx && headIdx < chunk.end) {
        nerString = chunk.label;
      }
    }
    // MENTION TYPE
    val mentionType = if (endIdx - startIdx == 1 && PronounDictionary.isDemonstrative(rawDoc.words(sentIdx)
      (headIdx))) {
      MentionType.DEMONSTRATIVE;
    } else if (endIdx - startIdx == 1 && (PronounDictionary.isPronLc(rawDoc.words(sentIdx)(headIdx).toLowerCase) ||
      config.languagePack.getPronominalTags.contains(rawDoc.pos(sentIdx)(headIdx)))) {
      MentionType.PRONOMINAL;
    } else if (nerString != "O" || config.languagePack.getProperTags.contains(rawDoc.pos(sentIdx)(headIdx))) {
      MentionType.PROPER;
    } else {
      MentionType.NOMINAL;
    }
    // GENDER AND NUMBER
    var number: Number = Number.SINGULAR;
    var gender: Gender = Gender.MALE;
    if (mentionType == MentionType.PRONOMINAL) {
      val pronLc = rawDoc.words(sentIdx)(headIdx).toLowerCase;
      gender = if (PronounDictionary.malePronouns.contains(pronLc)) {
        Gender.MALE
      } else if (PronounDictionary.femalePronouns.contains(pronLc)) {
        Gender.FEMALE
      } else if (PronounDictionary.neutralPronouns.contains(pronLc)) {
        Gender.NEUTRAL;
      } else {
        Gender.UNKNOWN;
      }
      number = if (PronounDictionary.singularPronouns.contains(pronLc)) {
        Number.SINGULAR
      } else if (PronounDictionary.pluralPronouns.contains(pronLc)) {
        Number.PLURAL;
      } else {
        Number.UNKNOWN;
      }
    } else {
      if (mentionType == MentionType.NOMINAL && config.usePOSForNumberCommon) {
        number = if (config.languagePack.isPluralPos(rawDoc.pos(sentIdx)(headIdx))) Number.PLURAL else Number.SINGULAR
      } else if (propertyComputer.ngComputer != null) {
        number = propertyComputer.ngComputer.computeNumber(rawDoc.words(sentIdx).slice(startIdx, endIdx),
          rawDoc.words(sentIdx)(headIdx));
        gender = if (nerString == "PERSON") {
          propertyComputer.ngComputer.computeGenderPerson(rawDoc.words(sentIdx).slice(startIdx, endIdx),
            headIdx - startIdx);
        } else {
          propertyComputer.ngComputer.computeGenderNonPerson(rawDoc.words(sentIdx).slice(startIdx, endIdx),
            rawDoc.words(sentIdx)(headIdx));
        }
      }
    }

    val mention = new Mention(rawDoc, mentIdx, sentIdx, startIdx, endIdx, headIdx, mentionType, nerString, number,
      gender)

    // calculate thesaurus Jos and Bims in advance
    if (propertyComputer.thesauri != null) {
      val jos = scala.collection.mutable.Map.empty[String, String]
      val bims = scala.collection.mutable.Map.empty[String, Set[String]]

      for (thesaurus <- propertyComputer.thesauri.all) {
        jos += thesaurus.identifier -> thesaurus.extractTerm(mention)
        bims += thesaurus.identifier -> thesaurus.extractContext(mention)
      }

      mention.termCache = jos.toMap
      mention.bimCache = bims.toMap

    }
    mention
  }
}

