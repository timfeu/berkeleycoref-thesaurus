package edu.berkeley.nlp.coref.preprocess

import edu.stanford.nlp.process.Morphology
import scala.collection.mutable.ArrayBuffer

object StanfordLemmatizer extends Lemmatizer {

  def lemmatize(words: Seq[Seq[String]], posTags: Seq[Seq[String]], lowercase: Boolean) = {
    val sentenceBuffer = new ArrayBuffer[Array[String]]

    for (sentenceRawPos <- words.view.zip(posTags)) {
      val lemmaBuffer = new ArrayBuffer[String]
      for (wordPos <- sentenceRawPos._1.zip(sentenceRawPos._2)) {
        lemmaBuffer += Morphology.lemmaStatic(wordPos._1, wordPos._2, lowercase).toLowerCase
      }
      sentenceBuffer += lemmaBuffer.toArray
    }

    sentenceBuffer.toArray
  }
}
