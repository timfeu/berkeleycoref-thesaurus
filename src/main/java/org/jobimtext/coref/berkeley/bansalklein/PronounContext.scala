package org.jobimtext.coref.berkeley.bansalklein

import java.io.{BufferedInputStream, FileInputStream}
import java.util.zip.GZIPInputStream

import edu.berkeley.nlp.coref.Mention
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.futile.util.Logger

import scala.io.Source

/**
 * Feature from section 3.5 of the Bansal & Klein paper.
 */
object PronounContext {
  val Placeholder = "<NONE>"

  val punctuationPos = Set(".", ",", "''")

  val FeatureFileOption = "featureFile"
  val R1BinSizeOption = "R1binSize"
  val R2BinSizeOption = "R2binSize"
  val R1GapBinSizeOption = "R1GapBinSize"

  var features: Option[Map[(String, String, String, String, Boolean), Array[String]]] = None

  /**
   * Returns true if the feature is defined for the given pair of mentions. This is the case if the current mention
   * is from a closed class, but the antecedent mention is not.
   *
   * @param current the current mention
   * @param antecedent a possible antecedent mention
   */
  def isFeatureApplicable(current: Mention, antecedent: Mention): Boolean = current.mentionType.isClosedClass && !antecedent.mentionType.isClosedClass

  def isPossessive(pronounMention: Mention): Boolean = pronounMention.headPos.endsWith("$")

  def getR1(pronounMention: Mention): String = getR(pronounMention, pronounMention.headIdx + 1)

  def getR2(pronounMention: Mention): String = getR(pronounMention, pronounMention.headIdx + 2)

  private def getR(mention: Mention, index: Int): String = {
    if (punctuationPos.contains(mention.accessPosOrPlaceholder(index))) Placeholder else mention.accessWordOrPlaceholder(index, Placeholder)
  }

  private def bin(d: Double, binSize: Double) = Math.round((d / binSize).toFloat)

  def loadFeatures(config: CorefSystemConfiguration): Unit = {
    val path = config.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("GeneralCoOccurence feature used without using precomputed " +
      "features")

    val source = path.endsWith(".gz") match {
      case false => Source.fromFile(path)
      case true => Source.fromInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(path))))
    }

    Logger.logss(s"[B&K] Loading pronoun context features from $path into memory...")

    val featureMap = source.getLines().map(_.split(' ')).map(parts => {
      assert(parts.length == 8)
      (parts(0).intern(), parts(1).intern(), parts(2).intern(), parts(3).intern(), parts(4).toBoolean) -> Array(parts(5), parts(6), parts(7))
    }).toMap
    source.close()

    features = Some(featureMap)
  }

  def getR1Feature(config: CorefSystemConfiguration, pronounMention: Mention, replacementMention: Mention) =
    getFeature(config, pronounMention, replacementMention, 0, config.getAdditionalProperty(getClass, R1BinSizeOption).asInstanceOf[Double])
  def getR2Feature(config: CorefSystemConfiguration, pronounMention: Mention, replacementMention: Mention) =
    getFeature(config, pronounMention, replacementMention, 1, config.getAdditionalProperty(getClass, R2BinSizeOption).asInstanceOf[Double])
  def getR1GapFeature(config: CorefSystemConfiguration, pronounMention: Mention, replacementMention: Mention) =
    getFeature(config, pronounMention, replacementMention, 2, config.getAdditionalProperty(getClass, R1GapBinSizeOption).asInstanceOf[Double])

  private def getFeature(config: CorefSystemConfiguration, pronounMention: Mention, replacementMention: Mention, featureIdx: Int, binSize: Double): String = {
    assert(isFeatureApplicable(pronounMention, replacementMention))
    if (!features.isDefined) loadFeatures(config)
    val tuple = (pronounMention.headString, replacementMention.headString, getR1(pronounMention), getR2(pronounMention), isPossessive(pronounMention))
    val feature = features.get.get(tuple)
    feature match {
      case None => throw new IllegalStateException("Feature missing for tuple " + tuple)
      case Some(featureValues) =>
        val featureValue = featureValues(featureIdx)
        if (featureValue.startsWith("M")) featureValue else bin(featureValue.toDouble, binSize).toString
    }
  }
}
