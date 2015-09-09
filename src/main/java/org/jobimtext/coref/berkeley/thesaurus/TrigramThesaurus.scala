package org.jobimtext.coref.berkeley.thesaurus

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.preprocess.Dependency
import org.jobimtext.coref.berkeley.{ExpansionIndexHolder, ThesaurusCache, DistributionalThesaurusComputer}
import org.jobimtext.api.struct.IThesaurusDatastructure
import edu.berkeley.nlp.coref.{PronounDictionary, BaseDoc, Mention}
import scala.collection.mutable
import scala.io.Source

/**
 * Thesaurus based on trigrams with the hole being the middle word.
 *
 * @param identifier application wide unique identifier for this thesaurus, e.g. "Trigram"
 *
 * @author Tim Feuerbach
 */
class TrigramThesaurus(val identifier: String, val cache: ThesaurusCache, val interface: IThesaurusDatastructure[String,
  String], val config: CorefSystemConfiguration) extends DistributionalThesaurusComputer {

  /**
   * Converts a single word to its Jo representation by returning the word itself without further processing.
   *
   * @param sentIdx the sentence index in the document
   * @param wordIdx the index in the sentence
   * @param rawDoc the document containing this particular word
   *
   */
  override def getTerm(sentIdx: Int, wordIdx: Int, rawDoc: BaseDoc): String = rawDoc.words(sentIdx)(wordIdx)

  /**
   * @inheritdoc
   *
   * <b>Realization:</b> Uses the head word as the trigram's center. Returns only one Bim (the holed trigram), starting
   * with "3-gram2" and the hole denoted by "_@_". An empty string will be used as the placeholder for
   * out-of-sentence-tokens.
   *
   * @param mention the mention to extract the Bims from
   * @return a set containing the string representation of the trigram with the mention's head in the middle
   */
  override def extractContext(mention: Mention): Set[String] = Set(trigram(mention.accessWordOrPlaceholder(
    mention.headIdx - 1, ""), mention.accessWordOrPlaceholder(mention.headIdx + 1, "")))

  protected def trigram(left: String, right: String) = "3-gram2(%s_@_%s)".format(left, right)

  /**
   * Extracts all properties of a mention's head in Jo form by searching the dependency relations for nn, appos,
   * nusbj and rcmod relations.
   *
   * @param mention the properties will be determined by this mention's head
   * @return a set of Jos.
   */
  override def extractAttributesOfHead(mention: Mention): Set[String] = {
    val deps = mention.rawDoc.sentenceToWordToDependencies(mention.sentIdx).getOrElse(mention.headIdx, Set.empty[Dependency])
    deps.collect {
      case Dependency(_, wordIndex, "nn", true) => wordIndex
      case Dependency(wordIndex, _, "nn", false) => wordIndex
      case Dependency(_, wordIndex, "appos", true) => wordIndex
      case Dependency(wordIndex, _, "nsubj", false) => wordIndex
      case Dependency(_, wordIndex, "rcmod", true) => wordIndex
    }.map(mention.rawDoc.getWord(mention.sentIdx, _).get).toSet
  }

  private def isNoun(pos: String) = config.languagePack.isNoun(pos)

  def filterContextExpandedTerm(jo: String): Boolean = {
    PronounDictionary.isDemonstrative(jo) || PronounDictionary.isPronLc(jo)
  }

  override def termToIsaRepresentation(jo: String): String = jo.toLowerCase

  protected val trigramNeighbors = """3-gram2\((\S*)_@_(\S*)\)""".r


  override def contextExpansion(bims: Set[String]): mutable.LinkedHashMap[String,
    ExpansionIndexHolder] = {
    val trigramNeighbors(left, right) = bims.iterator.next()

    val expandedBims = bims ++ config.languagePack.determiners.map(trigram(_, right))

    super.contextExpansion(filterStopWordBims(expandedBims))
  }

  lazy val stopWordList = {
    val file = Source.fromFile(config.getAdditionalProperty(classOf[DistributionalThesaurusComputer], "stopwordListPath").asInstanceOf[String])
    val stopWords = file.getLines().map(_.trim).filterNot(l => l.startsWith("#") || l.isEmpty).toSet
    file.close()
    stopWords
  }

  def isStopWord(word: String) = stopWordList.contains(word.toLowerCase)

  def filterStopWordBims(bims: Set[String]) = bims.filter(bim => {
    val parts = getBimWords(bim)
    !(isStopWord(parts(0)) && isStopWord(parts(1)))
  })

  def getBimWords(bim: String) = {
    val parts = bim.substring(7, bim.size).split("_@_")
    // we are not taking the content (8 to bims.size - 1) to split so we are aware of empty string and always have two parts
    parts(0) = if (parts(0).size > 1) parts(0).substring(1) else ""
    parts(1) = if (parts(1).size > 1) parts(1).substring(1) else ""
    parts
  }

  override def termCount(bim: String): Long = {
    val parts = getBimWords(bim)
    Math.round((interface.getTermCount(parts(0)) + interface.getTermCount(parts(1))) / 2.0)
  }

  override def computeOuterMentionContextFeatures(mention: Mention): Set[String] = {
    val left = if (mention.accessWordOrPlaceholder(mention.startIdx - 1) == Mention.StartWordPlaceholder) "" else mention.accessWordOrPlaceholder(mention.startIdx - 1)
    val right = if (mention.accessWordOrPlaceholder(mention.endIdx) == Mention.EndWordPlaceholder) "" else mention.accessWordOrPlaceholder(mention.endIdx + 1)
    Set(trigram(left, right))
  }
}
