package org.jobimtext.coref.uima

import de.tudarmstadt.ukp.dkpro.core.api.coref.`type`.{CoreferenceChain, CoreferenceLink}
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.`type`.pos.POS
import de.tudarmstadt.ukp.dkpro.core.api.ner.`type`.NamedEntity
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.`type`.Token
import de.tudarmstadt.ukp.dkpro.core.api.syntax.`type`.constituent._
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter
import edu.berkeley.nlp.coref.{BaseDoc, Chunk}
import edu.berkeley.nlp.coref.config.PredictionCorefSystemConfiguration
import edu.berkeley.nlp.coref.lang.{Language, LanguagePackFactory}
import edu.berkeley.nlp.futile.syntax.Tree
import edu.berkeley.nlp.futile.syntax.Trees.PennTreeRenderer
import org.apache.uima.UIMA_IllegalStateException
import org.apache.uima.fit.factory.{AnalysisEngineFactory, JCasFactory}
import org.apache.uima.fit.util.JCasUtil
import org.apache.uima.jcas.JCas
import org.apache.uima.jcas.cas.FSArray
import org.apache.uima.util.Logger
import org.jobimtext.coref.CorefSpec
import org.jobimtext.coref.berkeley.uima.CASToBaseDocConverter

import scala.collection.JavaConversions._

/**
 * Tests the [[CASToBaseDocConverter]].
 *
 * @author Tim Feuerbach
 */
class CASToBaseDocConverterSpec extends CorefSpec {
  val mockLogger = stub[Logger]
  val uut = new CASToBaseDocConverter(new PredictionCorefSystemConfiguration(LanguagePackFactory.getLanguagePack
    (Language.ENGLISH), null), mockLogger)
  val TestText = "Jane Doe won a prize for her paper."

  "A CasToBaseDocConverter" should "reject a CAS having token, but no constituent annotations" in {
    val jCas = createTokenizedCas()
    assume(JCasUtil.exists(jCas, classOf[Token]), "Tokens must be present")
    assume(!JCasUtil.exists(jCas, classOf[Constituent]), "constituents may not be present")

    intercept[UIMA_IllegalStateException] {
      uut.convert(jCas)
    }
  }

  it should "reject a CAS having constituent and token, but no POS annotations" in {
    val jCas = createTokenizedCas()
    annotateConstituents(jCas)

    assume(JCasUtil.exists(jCas, classOf[Token]), "tokens must be present")
    assume(JCasUtil.exists(jCas, classOf[Constituent]), "constituents must be present")
    assume(!JCasUtil.exists(jCas, classOf[POS]), "no POS annotations may be present")

    intercept[UIMA_IllegalStateException] {
      uut.convert(jCas)
    }
  }

  private val theDoc = uut.convert(createFullyAnnotatedCas())

  private val Words = IndexedSeq(IndexedSeq("Jane", "Doe", "won", "a", "prize", "for", "her", "paper", "."))
  private val POS = IndexedSeq(IndexedSeq("NNP", "NNP", "VBD", "DT", "NN", "IN", "PRP$", "NN", "."))
  private val NEChunks = IndexedSeq(IndexedSeq(Chunk[String](0, 2, "NAME")))
  private val CorefChunks = IndexedSeq(IndexedSeq(Chunk[Int](0, 2, 0), Chunk[Int](6, 7, 0)))

  "The resulting document" should "have all words" in {
    assert(theDoc.words == Words)
  }

  it should "have all POS" in {
    assert(theDoc.pos == POS)
  }

  it should "have all named entities" in {
    assert(theDoc.nerChunks == NEChunks)
  }

  it should "have all coref chunks" in {
    assert(theDoc.corefChunks == CorefChunks)
  }

  it should "have speaker placeholders" in {
    assert(theDoc.speakers.size == 1)
    assert(theDoc.speakers(0).size == Words(0).size)
    assert(theDoc.speakers(0).forall(_ == BaseDoc.UnknownSpeakerPlaceholder))
  }

  val firstSentenceTree = theDoc.trees(0)
  val FirstSentenceConstTree = {
    val tokens = Words(0).zip(POS(0)).map(wordAndPos => new Tree[String](wordAndPos._2,
      IndexedSeq(new Tree[String](wordAndPos._1))))

    val node_0_1 = createTree("NP", tokens(0), tokens(1))
    val node_3_4 = createTree("NP", tokens(3), tokens(4))
    val node_6_7 = createTree("NP", tokens(6), tokens(7))
    val node_5_7 = createTree("PP", tokens(5), node_6_7)
    val node_2_7 = createTree("VP", tokens(2), node_3_4, node_5_7)
    val s        = createTree("S", node_0_1, node_2_7, tokens(8))
    val root = createTree("ROOT", s)
    root
  }
  private val DependencyMap = Map(8 -> 2, 2 -> -1, 0 -> 1, 1 -> 2, 3 -> 4, 4 -> 2, 6 -> 7, 7 -> 5, 5 -> 2)
  assume(DependencyMap.keys.toSet == (0 until Words(0).size).toSet)

  "The first sentence tree" should "contain all words and POS tags" in {
    assert(firstSentenceTree.words == Words(0))
    assert(firstSentenceTree.pos == POS(0))
  }

  it should "contain the correct dependency mapping" in {
    assert(firstSentenceTree.childParentDepMap == DependencyMap, "\nSorted: " + firstSentenceTree.childParentDepMap.toSeq.sorted + " did not equal " + DependencyMap.toSeq.sorted)
  }

  it should "contain the correct parse" in {
    assert(firstSentenceTree.constTree == FirstSentenceConstTree, PennTreeRenderer.render(firstSentenceTree
      .constTree) + " did not equal the expected " + PennTreeRenderer.render(FirstSentenceConstTree))
  }

  private def createTree(label: String, children: Tree[String]*): Tree[String] = new Tree[String](label, children.toIndexedSeq)

  private def annotateConstituents(jCas: JCas): Unit = {
    // assuming the test text "Jane Doe won a prize for her paper ."
    val tokens = JCasUtil.select(jCas, classOf[Token]).toArray(Array.empty[Token])

    var tmpChildArray = new FSArray(jCas, 2)
    tmpChildArray.set(0, tokens(0))
    tmpChildArray.set(1, tokens(1))
    val node_0_1 = new NP(jCas, tokens(0).getBegin, tokens(1).getEnd)
    node_0_1.setConstituentType("NP")
    node_0_1.setChildren(tmpChildArray)
    node_0_1.addToIndexes()

    tmpChildArray = new FSArray(jCas, 2)
    tmpChildArray.set(0, tokens(3))
    tmpChildArray.set(1, tokens(4))
    val node_3_4 = new NP(jCas, tokens(3).getBegin, tokens(4).getEnd)
    node_3_4.setConstituentType("NP")
    node_3_4.setChildren(tmpChildArray)
    node_3_4.addToIndexes()

    tmpChildArray = new FSArray(jCas, 2)
    tmpChildArray.set(0, tokens(6))
    tmpChildArray.set(1, tokens(7))
    val node_6_7 = new NP(jCas, tokens(6).getBegin, tokens(7).getEnd)
    node_6_7.setConstituentType("NP")
    node_6_7.setChildren(tmpChildArray)
    node_6_7.addToIndexes()

    tmpChildArray = new FSArray(jCas, 2)
    tmpChildArray.set(0, tokens(5))
    tmpChildArray.set(1, node_6_7)
    val node_5_7 = new PP(jCas, tokens(5).getBegin, node_6_7.getEnd)
    node_5_7.setConstituentType("PP")
    node_5_7.setChildren(tmpChildArray)
    node_5_7.addToIndexes()

    tmpChildArray = new FSArray(jCas, 3)
    tmpChildArray.set(0, tokens(2))
    tmpChildArray.set(1, node_3_4)
    tmpChildArray.set(2, node_5_7)
    val node_2_7 = new VP(jCas, tokens(2).getBegin, node_5_7.getEnd)
    node_2_7.setConstituentType("VP")
    node_2_7.setChildren(tmpChildArray)
    node_2_7.addToIndexes()

    tmpChildArray = new FSArray(jCas, 3)
    tmpChildArray.set(0, node_0_1)
    tmpChildArray.set(1, node_2_7)
    tmpChildArray.set(2, tokens(8))
    val s = new S(jCas, 0, TestText.size)
    s.setConstituentType("S")
    s.setChildren(tmpChildArray)
    s.addToIndexes()

    tmpChildArray = new FSArray(jCas, 1)
    tmpChildArray.set(0, s)
    val root = new ROOT(jCas, 0, TestText.size)
    root.setConstituentType("ROOT")
    root.setChildren(tmpChildArray)
    root.addToIndexes()
  }

  private def createTokenizedCas(): JCas = {
    val jCas = JCasFactory.createJCas()
    jCas.setDocumentText(TestText)
    AnalysisEngineFactory.createEngine(classOf[BreakIteratorSegmenter]).process(jCas)
    jCas
  }

  private def annotatePos(jCas: JCas): Unit = {
    // assuming the test text "Jane Doe won a prize for her paper."
    val tokens = JCasUtil.select(jCas, classOf[Token]).toArray[Token](Array[Token]())
    addPos(jCas, tokens(0), "NNP")
    addPos(jCas, tokens(1), "NNP")
    addPos(jCas, tokens(2), "VBD")
    addPos(jCas, tokens(3), "DT")
    addPos(jCas, tokens(4), "NN")
    addPos(jCas, tokens(5), "IN")
    addPos(jCas, tokens(6), "PRP$")
    addPos(jCas, tokens(7), "NN")
    addPos(jCas, tokens(8), ".")
  }

  private def addPos(jCas: JCas, token: Token, posLabel: String): Unit = {
    val pos = new POS(jCas, token.getBegin, token.getEnd)
    pos.setPosValue(posLabel)
    pos.addToIndexes()
  }

  private def annotateNEs(jCas: JCas): Unit = {
    // assuming the test text "Jane Doe won a prize for her paper."
    val tokens = JCasUtil.select(jCas, classOf[Token]).toArray[Token](Array[Token]())
    val ne = new NamedEntity(jCas, tokens(0).getBegin, tokens(1).getEnd)
    ne.setValue("NAME")
    ne.addToIndexes()
  }

  private def annotateCoref(jCas: JCas): Unit = {
    // assuming the test text "Jane Doe won a prize for her paper."
    val tokens = JCasUtil.select(jCas, classOf[Token]).toArray[Token](Array[Token]())

    val mentionA = new CoreferenceLink(jCas, tokens(0).getBegin, tokens(1).getEnd)
    val mentionB = new CoreferenceLink(jCas, tokens(6).getBegin, tokens(6).getEnd)
    val chain = new CoreferenceChain(jCas)
    chain.setFirst(mentionA)
    mentionA.setNext(mentionB)

    mentionA.addToIndexes()
    mentionB.addToIndexes()
    chain.addToIndexes()
  }

  private def createFullyAnnotatedCas(): JCas = {
    val jCas = createTokenizedCas()
    annotatePos(jCas)
    annotateNEs(jCas)
    annotateCoref(jCas)
    annotateConstituents(jCas)
    jCas
  }
}
