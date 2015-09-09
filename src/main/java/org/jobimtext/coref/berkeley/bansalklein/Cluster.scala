package org.jobimtext.coref.berkeley.bansalklein

import edu.berkeley.nlp.coref.Mention
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import org.jobimtext.coref.berkeley.bansalklein.provider.{FeatureProvider, SimpleDatabaseFeatureProvider, SimpleStoreFeatureProvider}

object Cluster {
  val FeatureFileOption = "featureFile"
  var provider: FeatureProvider = null

  def getFeature(config: CorefSystemConfiguration, m1: Mention, m2: Mention): String = {
    val h1 = m1.headString
    val h2 = m2.headString
    val path = config.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("Cluster feature used without using precomputed " +
      "features")

    if (provider == null) provider = if (path.endsWith(".db")) new SimpleDatabaseFeatureProvider(path, "bkCluster") else new SimpleStoreFeatureProvider(path, HeadPairFeatureStore.ClusterFeatureIdx)

    provider.getFeature(h1, h2) match {
      case None => throw new IllegalStateException(
        s"Precomputed features for cluster did not contain pair $h1 $h2")
      case Some(featureValue) => featureValue
    }
  }
}
