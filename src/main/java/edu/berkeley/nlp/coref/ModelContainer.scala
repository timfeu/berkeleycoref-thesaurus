package edu.berkeley.nlp.coref

import java.io.{ObjectOutput, ObjectInput, Externalizable}

import org.jobimtext.coref.berkeley.ThesaurusFeature

import scala.collection.mutable

/**
 * Stores a trained model together with thesaurus information.
 *
 * TODO use a more robust storage model that supports versioning
 *
 * @author Tim Feuerbach
 */
@SerialVersionUID(1L)
class ModelContainer extends Externalizable {

  var scorer: PairwiseScorer = null
  var thesaurusFeatures: Array[ThesaurusFeature] = null

  def this(scorer: PairwiseScorer, thesaurusFeatures: Array[ThesaurusFeature]) {
    this()
    this.scorer = scorer
    this.thesaurusFeatures = thesaurusFeatures
  }

  override def writeExternal(out: ObjectOutput): Unit = {
    out.writeObject(scorer)

    // WRITE THESAURUS FEATURES
    out.writeInt(thesaurusFeatures.size)
    for (thesaurusFeature <- thesaurusFeatures) {
      out.writeObject(thesaurusFeature.thesaurusId)
      out.writeObject(thesaurusFeature.featureName)
      out.writeInt(thesaurusFeature.options.size)
      for (option <- thesaurusFeature.options) {
        out.writeObject(option._1)
        out.writeObject(option._2)
      }
    }
  }

  override def readExternal(in: ObjectInput): Unit = {
    scorer = in.readObject().asInstanceOf[PairwiseScorer]

    // READ THESAURUS FEATURES
    val numFeatures = in.readInt()
    val thesaurusFeatureBuffer = mutable.ArrayBuffer.empty[ThesaurusFeature]
    for (i <- 0 until numFeatures) {
      val thesaurusId = in.readObject().asInstanceOf[String]
      val featureName = in.readObject().asInstanceOf[String]
      val numOptions = in.readInt()
      var options = Map.empty[String, String]
      for (j <- 0 until numOptions) {
        val key = in.readObject().asInstanceOf[String]
        val value = in.readObject().asInstanceOf[String]
        options += key -> value
      }

      thesaurusFeatureBuffer += ThesaurusFeature(thesaurusId, featureName, options)
    }

    this.thesaurusFeatures = thesaurusFeatureBuffer.toArray
  }
}
