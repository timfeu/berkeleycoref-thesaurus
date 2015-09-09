package edu.berkeley.nlp.coref.preprocess

/**
 * TODO doc
 * @author Tim Feuerbach
 */
trait Lemmatizer {
  def lemmatize(words: Seq[Seq[String]], posTags: Seq[Seq[String]], lowercase: Boolean): Array[Array[String]]
}
