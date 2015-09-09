package org.jobimtext.coref.berkeley.thesaurus

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.preprocess.Dependency
import edu.berkeley.nlp.coref.{BaseDoc, Mention}
import org.jobimtext.api.struct.IThesaurusDatastructure
import org.jobimtext.coref.berkeley.{DistributionalThesaurusComputer, ExpansionIndexHolder, ThesaurusCache}

import scala.collection.mutable

/**
 * Holing operation implementation that uses collapsed, labeled dependency graphs
 * as produced by the Stanford Parser. The context features are all direct neighbors
 * of a word in the dependency graph in term representation plus their label and
 * the direction (governing or governed). The terms are all words, lemmatized and
 * supplemented with their part of speech tag in a canonicalized form.
 *
 * You may obtain the corresponding pretrained thesaurus model at:
 *
 * http://sourceforge.net/p/jobimtext/wiki/models/
 *
 * It is named "en_news120M".
 *
 * Currently, the implementation only supports the english
 *
 * @example "The dog barks." contains three terms: "the#DT", "dog#NN" and "bark#VB".
 *
 * @author Tim Feuerbach
 */
class StanfordDependencyThesaurus(val identifier: String, protected val cache: ThesaurusCache,
                                  protected val interface: IThesaurusDatastructure[String,
                                    String], val config: CorefSystemConfiguration) extends
DistributionalThesaurusComputer {

  /**
   * @inheritdoc
   *
   * The Jo is the word together with its part of speech tag. Nouns, adjectives and verbs will
   * be lemmatized.
   */
  override def getTerm(sentIdx: Int, wordIdx: Int, rawDoc: BaseDoc): String = {
    val wordString = if (shouldLemmatize(rawDoc.pos(sentIdx)(wordIdx))) rawDoc.lemmas(sentIdx)(wordIdx).toLowerCase
    else
      rawDoc.words(sentIdx)(wordIdx).toLowerCase

    wordString + "#" + convertPosTag(rawDoc.pos(sentIdx)(wordIdx))
  }

  /**
   * @inheritdoc
   *
   * The Bims are all collapsed dependency relations of which the mention's head is a member of, regardless
   * whether it is governed or governs.
   */
  override def extractContext(mention: Mention): Set[String] = mention.rawDoc.sentenceToWordToDependencies(mention
    .sentIdx).getOrElse(mention.headIdx, Set()).map {
    case Dependency(govIdx, depIdx, label, isGovernor) => {
      val partnerIdx = if (isGovernor) depIdx else govIdx
      val partnerPos = mention.rawDoc.pos(mention.sentIdx)(partnerIdx)
      val partnerWord = if (shouldLemmatize(partnerPos)) mention.rawDoc.lemmas(mention.sentIdx)(partnerIdx)
      else
        mention.rawDoc.words(mention.sentIdx)(partnerIdx)

      partnerWord.toLowerCase + "#" + convertPosTag(partnerPos) + "#" + (if (isGovernor) "" else "-") + label
    }
  }.toSet

  private def idxInMention(mention: Mention, idx: Int) = idx >= mention.startIdx && idx < mention.endIdx

  override def computeOuterMentionContextFeatures(mention: Mention): Set[String] = filterRerankBims(mention.rawDoc.sentenceToWordToDependencies(mention
    .sentIdx).getOrElse(mention.headIdx, Set()).collect {
    case Dependency(govIdx, depIdx, label, isGovernor) if (mention.headIdx == govIdx && !idxInMention(mention, depIdx)) || (mention.headIdx == depIdx && !idxInMention(mention, govIdx)) => {
      val partnerIdx = if (isGovernor) depIdx else govIdx
      val partnerPos = mention.rawDoc.pos(mention.sentIdx)(partnerIdx)
      val partnerWord = if (shouldLemmatize(partnerPos)) mention.rawDoc.lemmas(mention.sentIdx)(partnerIdx)
      else
        mention.rawDoc.words(mention.sentIdx)(partnerIdx)

      partnerWord.toLowerCase + "#" + convertPosTag(partnerPos) + "#" + (if (isGovernor) "" else "-") + label
    }
  }.toSet)

  protected def shouldLemmatize(pos: String) = config.languagePack.isAdjective(pos) || config.languagePack.isNoun(pos) ||
    config.languagePack.isVerb(pos)

  override def termToIsaRepresentation(jo: String): String = {
    val idxOfHash = jo.indexOf('#')
    if (idxOfHash >= 0) {
      jo.substring(0, idxOfHash)
    } else {
      jo
    }
  }

  /**
   * The part of speech tag in the database only considers singular elements, no tense, and shortens "NNP" and the
   * like to "NP".
   *
   * @param posTag the pos tag
   *
   * @return the shortened pos tag
   */
  protected def convertPosTag(posTag: String) = posTag match {
    case "NNS" => "NN"
    case "NNP" | "NNPS" => "NP"
    case "VBD" | "VBG" | "VBN" | "VBP" | "VBZ" => "VB"
    case "JJR" | "JJS" => "JJ"
    case default => default
  }

  val FilterPos = Set("WP", "WP$", "PRP", "PRP$", "WDT", "DT")

  def filterContextExpandedTerm(term: String): Boolean = {
    val idxOfHash = term.indexOf('#')
    if (idxOfHash >= 0) {
      !FilterPos.contains(term.substring(idxOfHash + 1))
    } else {
      true
    }
  }


  /**
   * @inheritdoc
   *
   * Filters out determiners. They are uninformative context features and
   * slowing down the computation time unnecessarily.
   */
  override def contextExpansion(context: Set[String]): mutable.LinkedHashMap[String,
    ExpansionIndexHolder] = super.contextExpansion(context.filterNot(_.endsWith("det")))

  /**
   * Extracts all properties of a mention's head in Jo form. Checks for nn, -nn,
   * amod and copula denpendency relations in
   * the mention's head's Bims and returns the corresponding partner Jos.
   *
   * @param mention the properties will be determined by this mention's head
   * @return a set of Jos.
   */
  override def extractAttributesOfHead(mention: Mention): Set[String] = getContext(mention).filter(isPropertyDependency)
    .map(dep => dep.substring(0, dep.lastIndexOf("#")))

  override def extractIncompatibleAttributesOfHead(mention: Mention): Set[String] = getContext(mention).filter(bim => isPropertyDependency(bim) || bim.endsWith("#JJ#amod"))

  private def isPropertyDependency(bim: String) = bim.endsWith("#nn") || bim.endsWith("#-nn") ||
    bim.endsWith("#appos") || bim.endsWith("#NN#-nsubj") || bim.endsWith("#rcmod")


  def filterRerankBims(bims: Set[String]): Set[String] = bims.filter(bim => {
    val parts = bim.split('#')
    val pos = parts(parts.size - 2)
    pos.startsWith("V") || pos.startsWith("J") || pos.startsWith("N") || pos.startsWith("R")
  })



  override def termCount(bim: String): Long = interface.getTermCount(bim.substring(0, bim.lastIndexOf('#')))
}
