package edu.berkeley.nlp.coref.preprocess

import org.jobimtext.lemmatizer.PosLemmatizer

import scala.collection.mutable.ArrayBuffer

/**
 * TODO make class, inherit trait, use factory
 */
object PatriciaTrieLemmatizer extends Lemmatizer {
  lazy val posLemmatizer = PosLemmatizer.createEnglishNounVerbAdjectiveLemmatizer()

  def lemmatize(words: Seq[Seq[String]], posTags: Seq[Seq[String]], lowercase: Boolean) = {
    val sentenceBuffer = new ArrayBuffer[Array[String]]

    for (sentenceRawPos <- words.view.zip(posTags)) {
      val lemmaBuffer = new ArrayBuffer[String]
      for (wordPos <- sentenceRawPos._1.zip(sentenceRawPos._2)) {
        lemmaBuffer += posLemmatizer.lemmatizeWord(wordPos._1.toLowerCase, wordPos._2).toLowerCase
      }
      sentenceBuffer += lemmaBuffer.toArray
    }

    sentenceBuffer.toArray
  }
}
