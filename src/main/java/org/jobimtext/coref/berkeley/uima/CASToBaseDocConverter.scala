package org.jobimtext.coref.berkeley.uima

import de.tudarmstadt.ukp.dkpro.core.api.coref.`type`.CoreferenceChain
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.`type`.pos.POS
import de.tudarmstadt.ukp.dkpro.core.api.ner.`type`.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.`type`.Token
import de.tudarmstadt.ukp.dkpro.core.api.syntax.`type`.constituent.Constituent
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.{BaseDoc, Chunk, DepConstTree, HeadFinderFactory}
import edu.berkeley.nlp.futile.syntax.Tree
import org.apache.uima.UIMA_IllegalStateException
import org.apache.uima.fit.util.JCasUtil._
import org.apache.uima.jcas.JCas
import org.apache.uima.util.{Level, Logger}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Converts UIMA CASes to Berkeley Coref [[edu.berkeley.nlp.coref.BaseDoc]]s.
 * <p>
 * The CAS has to be annotated with the following types to produce non-empty results:
 *
 * <ul>
 * <li><code>de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token</code></li>
 * <li><code>de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS</code><</li>
 * <li><code>de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent</code><</li>
 * </ul>
 *
 * Additionally, <code>de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity</code> annotations should be used,
 * as they increase the mention prediction accuracy; however note that in the standard settings (SURFACE or FINAL), the
 * Berkeley system has no feature targeting the named entity labels.
 *
 * @param config coreference configuration
 * @param logger UIMA logger
 *
 * @author Tim Feuerbach
 */
class CASToBaseDocConverter(val config: CorefSystemConfiguration, logger: Logger) {

  val headFinder = HeadFinderFactory.create(config.getLanguagePack.getLanguage)

  /*
   * Keys of error messages
   */
  val MessageDigest = "org.jobimtext.coref.berkeley.uima.CASToBaseDocConverter_Messages"
  val NoTokensForNEErrorKey = "no_tokens_for_ne"
  val NoPosOnTokenErrorKey = "no_pos_on_token"
  val TokensButNoConstituentsErrorKey = "tokens_but_no_constituents"

  /**
   * Converts a single CAS document to a BaseDoc for further processing by the coreference system.
   *
   * As a side effect, collects the sentence-word indices of [[Token]]s, e.g. theMap(2)(1) returns the token of
   * the second word of the third sentence. This is just for convenience; since we iterate over sentences anyway,
   * we may collect the token indices which are required to parse corefence clusterings returned by the system.
   *
   * @param jCas the CAS document to process
   *
   * @return A BaseDocument representing the CAS, and for convenience, a Map mapping from sentence indices to word
   *         indices to tokens.
   */
  @throws[UIMA_IllegalStateException]("If their is a discrepancy between various type spans")
  def convert(jCas: JCas): (BaseDoc, Map[Int, Map[Int, Token]]) = {
    if (exists(jCas, classOf[Token]) && !exists(jCas, classOf[Constituent])) throw new UIMA_IllegalStateException
    (MessageDigest, TokensButNoConstituentsErrorKey, Array.empty[AnyRef])

    val words = new ArrayBuffer[IndexedSeq[String]]
    val pos = new ArrayBuffer[IndexedSeq[String]]
    val trees = new ArrayBuffer[DepConstTree]
    val neChunks = new ArrayBuffer[IndexedSeq[Chunk[String]]]

    val tokenToSentenceIdx = mutable.Map.empty[Token, Int]
    val sentenceToTokenToWordIdx = mutable.Map.empty[Int, mutable.Map[Token, Int]]
    val sentenceToWordToTokenIdx = mutable.Map.empty[Int, Map[Int, Token]]

    // Iterate over sentences
    var sentIdx = 0
    // TODO it seems that not all DKPro constituent parser AEs are correctly using the specialized constituent
    // classes (e.g. BerkeleyParser)
    // the filter is a workaround until DKPro version 1.7 has been released
    for (root <- select(jCas, classOf[Constituent]).filter(_.getConstituentType == "ROOT")) {
      // collect the tokens' index relative to the sentence to properly build NE chunks
      val tokenToWordIdx = mutable.Map.empty[Token, Int]
      val wordToTokenIdx = mutable.Map.empty[Int, Token]

      // collect words and POS
      val thisSentenceWords = new ArrayBuffer[String]
      val thisSentencePOS = new ArrayBuffer[String]

      var tokenIdx = 0
      for (token <- selectCovered(classOf[Token], root)) {
        tokenToWordIdx += token -> tokenIdx
        wordToTokenIdx += tokenIdx -> token
        tokenToSentenceIdx += token -> sentIdx

        thisSentenceWords += token.getCoveredText
        val posTag = selectCovered(classOf[POS], token)
        if (posTag.size == 0) throw new UIMA_IllegalStateException(MessageDigest,
          NoPosOnTokenErrorKey, Array[AnyRef](new Integer(token.getBegin), new Integer(token.getEnd)))
        thisSentencePOS += posTag.get(0).getPosValue
        tokenIdx += 1
      }
      sentenceToTokenToWordIdx(sentIdx) = tokenToWordIdx
      sentenceToWordToTokenIdx(sentIdx) = wordToTokenIdx.toMap

      val wordsArray = thisSentenceWords.toIndexedSeq
      val posArray = thisSentencePOS.toIndexedSeq

      words += wordsArray
      pos += posArray

      if (words.isEmpty) logger.log(Level.FINE, "Encountered empty sentence among roots")

      // collect NEs
      val thisSentenceNEs = new ArrayBuffer[Chunk[String]]
      for (ne <- selectCovered(classOf[NamedEntity], root)) {
        val tokensCoveredByNe = selectCovered(classOf[Token], ne)

        if (tokensCoveredByNe.isEmpty) {
          throw new UIMA_IllegalStateException(MessageDigest, NoTokensForNEErrorKey,
            Array[AnyRef](new Integer(ne.getBegin),
              new Integer(ne.getEnd), root, ne.getCoveredText))
        }

        val begin = tokenToWordIdx(tokensCoveredByNe.head)
        val end = tokenToWordIdx(tokensCoveredByNe.last) + 1

        thisSentenceNEs += Chunk(begin, end, ne.getValue)
      }

      neChunks += thisSentenceNEs.toIndexedSeq

      // add tree
      trees += createTree(root, posArray, wordsArray)

      sentIdx += 1
    }

    // assemble coref chunks
    // since coreference links do not hold a reference to the chain itself, we can't do this step inside the sentence
    // loop
    val corefChunks = Array.fill(words.size)(IndexedSeq.empty[Chunk[Int]])

    // assign each entity (corefence chain) a unique id
    var chainId = 0
    for (corefChain <- select(jCas, classOf[CoreferenceChain])) {
      var link = corefChain.getFirst

      while (link != null) {
        val coveredTokens = selectCovered(jCas, classOf[Token], link)
        require(coveredTokens.size > 0, "Coreference annotations must cover at least one token")
        require(coveredTokens.map(tokenToSentenceIdx).toSet.size == 1, "all tokens covered by a mention must be from " +
          "the same sentence")

        val sentIdx = tokenToSentenceIdx(coveredTokens(0))
        val begin = sentenceToTokenToWordIdx(sentIdx)(coveredTokens.head)
        val end = sentenceToTokenToWordIdx(sentIdx)(coveredTokens.last) + 1

        corefChunks(sentIdx) :+= Chunk[Int](begin, end, chainId)

        link = link.getNext
      }
      chainId += 1
    }

    // not sure how robust the system is, but we better sort the chain in the order they appear in the sentence
    // before getting in trouble
    val corefChunksSorted = corefChunks.map(_.sortBy(_.start))

    // finally, create the document
    (new BaseDoc(CASToBaseDocConverter.DefaultDocId,
      CASToBaseDocConverter.DefaultPartNumber,
      words.toIndexedSeq,
      pos.toIndexedSeq,
      trees.toIndexedSeq,
      neChunks.toIndexedSeq,
      corefChunksSorted.toIndexedSeq,
      words.map(sent => IndexedSeq.fill(sent.size)(BaseDoc.UnknownSpeakerPlaceholder)).toIndexedSeq,
      Array.fill(words.size)(IndexedSeq.empty[String]).toIndexedSeq
    ), sentenceToWordToTokenIdx.toMap)
  }

  def createTree(root: Constituent, pos: IndexedSeq[String], words: IndexedSeq[String]): DepConstTree = {
    val rootTree = TreeBuilder.buildTree(root)
    new DepConstTree(rootTree, pos, words, DepConstTree.extractDependencyStructure(rootTree, headFinder))
  }
}

object CASToBaseDocConverter {
  /**
   * DocId used for all documents produced by the converter. Should not start with bc or wb - the Berkeley system
   * assumes this to be a conversation document!
   */
  val DefaultDocId = ""

  /**
   * Part number used for all documents produced by the coverter
   */
  val DefaultPartNumber = 0
}