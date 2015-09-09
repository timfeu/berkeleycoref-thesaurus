package org.jobimtext.coref.berkeley.bansalklein

import java.io.{BufferedInputStream, FileInputStream}
import java.util.zip.GZIPInputStream

import edu.berkeley.nlp.coref.Mention
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.futile.util.Logger

import scala.io.Source

object Incompatibility {
  var features: Option[Map[(String, String), String]] = None

  val FeatureFileOption = "featureFile"

  private def loadFeatures(configuration: CorefSystemConfiguration): Unit = {
    val path = configuration.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("Web incompatibility feature used without using precomputed " +
      "features")

    val source = path.endsWith(".gz") match {
      case false => Source.fromFile(path)
      case true => Source.fromInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(path))))
    }

    Logger.logss(s"Loading incompatibility features from $path into memory...")
    val featureMap = source.getLines().map(_.split(' ')).map(parts => {
      assert(parts.length == 3)
      (parts(0), parts(1)) -> parts(2)
    }).toMap
    source.close()

    features = Some(featureMap)

    Logger.logss("Done, loaded " + featureMap.size + " features")

  }

  def getFeature(config: CorefSystemConfiguration, m1: Mention, m2: Mention): String = {
    if (!features.isDefined) {
      loadFeatures(config)
    }

    val h1 = m1.headString
    val h2 = m2.headString

    features.get.get((h1, h2)) match {
      case None => throw new IllegalStateException(
        s"Precomputed features for incompatibility did not contain pair $h1 $h2")
      case Some(featureValue) => featureValue
    }
  }
}
