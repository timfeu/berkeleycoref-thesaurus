package edu.berkeley.nlp.coref.io.impl

import java.io.{BufferedReader, InputStreamReader}

import edu.berkeley.nlp.coref.{BaseDoc, Chunk}
import edu.berkeley.nlp.coref.lang.Language
import edu.berkeley.nlp.futile.syntax.Tree
import edu.berkeley.nlp.futile.syntax.Trees.PennTreeRenderer
import org.jobimtext.coref.CorefSpec

import scala.collection.JavaConversions._

/**
 * Specifies the behaviour of the CoNLL document reader, primarly to fix the semantics of BaseDocs.
 *
 * @author Tim Feuerbach
 */
class ConllDocReaderSpec extends CorefSpec {
  val uut = new ConllDocReader(Language.ENGLISH, true)

  private val stream = Thread.currentThread().getContextClassLoader.getResourceAsStream("org/jobimtext/coref/example" +
    ".conll")

  assume(stream != null)

  val result = uut.loadDocs(new BufferedReader(new InputStreamReader(stream)))

  private val Words = IndexedSeq(IndexedSeq("In", "another", "moment", "down", "went", "Alice", "after", "it", "," +
    "", "never", "once", "considering", "how", "in", "the", "world", "she", "was", "to", "get", "out", "again", "."))

  private val Pos = IndexedSeq(IndexedSeq("IN", "DT", "NN", "RB", "VBD", "NNP", "IN", "PRP", ",", "RB", "RB", "VBG",
    "WRB", "IN", "DT", "NN", "PRP", "VBD", "TO", "VB", "RP", "RB", "."))

  private val NerChunks = IndexedSeq(IndexedSeq(Chunk[String](5, 6, "MISC")))

  private val Speakers = IndexedSeq(IndexedSeq.fill(23)(BaseDoc.UnknownSpeakerPlaceholder).toIndexedSeq)

  private val DocId = "in_raw/example.txt"
  private val DocPartNo = 0

  private val CorefChunks = IndexedSeq(IndexedSeq(Chunk[Int](5, 6, 1), Chunk[Int](16, 17, 1)))

  private val FirstSentenceDepMap = Map(0 -> 4, 5 -> 4, 10 -> 11, 14 -> 15, 20 -> 19, 1 -> 2, 6 -> 4, 21 ->
    19, 9 -> 11, 13 -> 17, 2 -> 0, 17 -> 12, 22 -> 4, 12 -> 11, 7 -> 6, 3 -> 4, 18 -> 17, 16 -> 17, 11 -> 4, 8 -> 4,
    19 -> 18, 4 -> -1, 15 -> 13)

  "A ConllDocReader" should "return the proper number of documents contained in a file" in {
    assert(result.size == 1)
  }

  val theDoc = result.head

  "The resulting BaseDoc" should "have all the words" in {
    assert(theDoc.words == Words)
  }

  it should "have all the POS tags" in {
    assert(theDoc.pos == Pos)
  }

  it should "have all the named entities" in {
    assert(theDoc.nerChunks == NerChunks)
  }

  it should "have the proper doc id and part number" in {
    assert(theDoc.docID == DocId)
    assert(theDoc.docPartNo == DocPartNo)
  }

  // Note: adopting the entity numbering is not hard requirement; the behaviour could be changed without breaking the
  // system
  it should "have the correct coreference spans with entity numbering taken from the document" in {
    assert(theDoc.corefChunks == CorefChunks)
  }

  it should "assign the correct speakers" in {
    assert(theDoc.speakers == Speakers)
  }

  val firstSentenceTree = theDoc.trees(0)
  private val FirstSentenceConstTree = {
    val tokenLeafs = Words(0).zip(Pos(0)).map(wordAndPos => new Tree[String](wordAndPos._2,
      IndexedSeq(new Tree[String](wordAndPos._1))))

    // depth 1 nodes
    val node_3 = new Tree[String]("NP", IndexedSeq(tokenLeafs(3)))
    val node_5 = new Tree[String]("NP", IndexedSeq(tokenLeafs(5)))
    val node_7 = new Tree[String]("NP", IndexedSeq(tokenLeafs(7)))
    val node_9 = new Tree[String]("ADVP", IndexedSeq(tokenLeafs(9)))
    val node_10 = new Tree[String]("ADVP", IndexedSeq(tokenLeafs(10)))
    val node_12 = new Tree[String]("WHADVP", IndexedSeq(tokenLeafs(12)))
    val node_16 = new Tree[String]("NP", IndexedSeq(tokenLeafs(16)))
    val node_20 = new Tree[String]("PRT", IndexedSeq(tokenLeafs(20)))
    val node_21 = new Tree[String]("ADVP", IndexedSeq(tokenLeafs(21)))
    val node_1_2 = new Tree[String]("NP", IndexedSeq(tokenLeafs(1), tokenLeafs(2)))
    val node_14_15 = new Tree[String]("NP", IndexedSeq(tokenLeafs(14), tokenLeafs(15)))

    // depth 2 nodes
    val node_0_2 = new Tree[String]("PP", IndexedSeq(tokenLeafs(0), node_1_2))
    val node_6_7 = new Tree[String]("PP", IndexedSeq(tokenLeafs(6), node_7))
    val node_13_15 = new Tree[String]("PP", IndexedSeq(tokenLeafs(13), node_14_15))
    val node_19_21 = new Tree[String]("VP", IndexedSeq(tokenLeafs(19), node_20, node_21))
    val node_18_21b = new Tree[String]("VP", IndexedSeq(tokenLeafs(18), node_19_21))

    // depth 3 nodes
    val node_18_21 = new Tree[String]("S", IndexedSeq(node_18_21b))

    // depth n-6 nodes
    val node_17_21 = new Tree[String]("VP", IndexedSeq(tokenLeafs(17), node_18_21))

    // depth n-5 nodes
    val node_13_21 = new Tree[String]("S", IndexedSeq(node_13_15, node_16, node_17_21))

    // depth n-4 nodes
    val node_12_21 = new Tree[String]("SBAR", IndexedSeq(node_12, node_13_21))

    // depth n-3 nodes
    val node_10_21 = new Tree[String]("VP", IndexedSeq(node_10, tokenLeafs(11), node_12_21))

    // depth n-2 nodes
    val node_9_21 = new Tree[String]("S", IndexedSeq(node_9, node_10_21))

    // depth n-1 nodes
    val node_4_21 = new Tree[String]("VP", IndexedSeq(tokenLeafs(4), node_5, node_6_7, tokenLeafs(8), node_9_21))

    // depth n nodes
    val node_0_22 = new Tree[String]("S", IndexedSeq(node_0_2, node_3, node_4_21, tokenLeafs(22)))

    // root
    val root = new Tree[String]("ROOT", IndexedSeq(node_0_22))

    root
  }

  "The document's tree" should "have all correct dependencies" in {
    assert(firstSentenceTree.childParentDepMap.toMap == FirstSentenceDepMap)
  }

  it should "mirror all words from the sentence" in {
    assert(firstSentenceTree.words == Words(0))
  }

  it should "mirror all pos tags from the sentence" in {
    assert(firstSentenceTree.pos == Pos(0))
  }

  it should "build the tree correctly" in {
    assert(firstSentenceTree.constTree == FirstSentenceConstTree, PennTreeRenderer.render(firstSentenceTree
      .constTree) + " did not equal the expected " + PennTreeRenderer.render(FirstSentenceConstTree))
  }
}
