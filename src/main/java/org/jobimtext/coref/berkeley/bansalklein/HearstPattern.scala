package org.jobimtext.coref.berkeley.bansalklein

import edu.berkeley.nlp.coref.Mention
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import org.jobimtext.coref.berkeley.bansalklein.provider.{FeatureProvider, SimpleDatabaseFeatureProvider, SimpleStoreFeatureProvider}

/**
 * The Bansal & Klein Hearst pattern features (section 3.2 of the B&K paper).
 */
object HearstPattern {
  val FeatureFileOption = "featureFile"
  var provider: FeatureProvider = null

  def getFeature(config: CorefSystemConfiguration, m1: Mention, m2: Mention): String = {
    val h1 = m1.headString
    val h2 = m2.headString

    val path = config.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("HearstPattern feature used without using precomputed " +
      "features")

    if (provider == null) if (path.endsWith(".db")) new SimpleDatabaseFeatureProvider(path, "bkHearst") else new SimpleStoreFeatureProvider(path, HeadPairFeatureStore.HearstFeatureIdx)

    provider.getFeature(h1, h2) match {
      case None => throw new IllegalStateException(
        s"Precomputed features for Hearst did not contain pair $h1 $h2")
      case Some(featureValue) => featureValue
    }
  }
}
