package edu.berkeley.nlp.coref
import edu.berkeley.nlp.coref.preprocess.{PatriciaTrieLemmatizer,
PennTrebankToStanfordDependenciesConverter}

/**
 * Chunks are semi-inclusive intervals spanning the word indices [start, end).
 *
 * @param start index of the first word that starts the chunk, relative to the sentence it is contained in
 * @param end index of word following the last word that starts the chunk, relative to the sentence it is contained in
 * @param label The chunk's label (e.g. CURRENCY for NEs)
 *
 * @tparam T type of the chunk label (e.g. String for named entities)
 */
case class Chunk[T](start: Int,
                    end: Int,
                    label: T);

// rawText should only be used to save trouble when outputting the document
// for scoring; never at any other time!
case class BaseDoc(docID: String,
                   docPartNo: Int,
                    words: IndexedSeq[IndexedSeq[String]],
                    pos: IndexedSeq[IndexedSeq[String]],
                    trees: IndexedSeq[DepConstTree],
                    nerChunks: IndexedSeq[IndexedSeq[Chunk[String]]],
                    corefChunks: IndexedSeq[IndexedSeq[Chunk[Int]]],
                    speakers: IndexedSeq[IndexedSeq[String]],
                    rawText: IndexedSeq[IndexedSeq[String]]) {

  val numSents = words.size;

  lazy val stanfordDependencies = PennTrebankToStanfordDependenciesConverter.computeDependencies(trees.map(_.constTree))

  lazy val lemmas = PatriciaTrieLemmatizer.lemmatize(words, pos, lowercase = false)

  lazy val sentenceToWordToDependencies = PennTrebankToStanfordDependenciesConverter.createWordToDependencyMap(stanfordDependencies)

  def printableDocName = docID + " (part " + docPartNo + ")";

  def isConversation = docID.startsWith("bc") || docID.startsWith("wb")

  /**
   * Optionally returns the word at the given position.
   *
   * @param sentIdx The sentence index in the document
   * @param wordIdx The word index relative to the sentence (so 0 is the first word in the sentence)
   *
   * @return None if any of the indices is out of bounds, Some(String) containing the word otherwise
   */
  def getWord(sentIdx: Int, wordIdx: Int): Option[String] = {
    if (sentIdx < 0 || sentIdx > words.size) None else {
      val sent = words(sentIdx)
      if (wordIdx < 0 || wordIdx > sent.size) None else {
        Some(sent(wordIdx))
      }
    }
  }

  /**
   * Optionally returns the pos of the word at the given position.
   *
   * @param sentIdx The sentence index in the document
   * @param wordIdx The word index relative to the sentence (so 0 is the first word in the sentence)
   *
   * @return None if any of the indices is out of bounds, Some(String) containing the word's pos otherwise
   */
  def getPos(sentIdx: Int, wordIdx: Int): Option[String] = {
    if (sentIdx < 0 || sentIdx >= pos.size) None else {
      val sent = pos(sentIdx)
      if (wordIdx < 0 || wordIdx >= sent.size) None else {
        Some(sent(wordIdx))
      }
    }
  }
}

object BaseDoc {
  val UnknownSpeakerPlaceholder = "-"
}