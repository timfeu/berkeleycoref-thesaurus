package org.jobimtext.coref.berkeley.bansalklein

/**
 * @author Mohit Bansal (EECS, UC Berkeley)
 *         Coreference Semantics from Web Features (Bansal and Klein, ACL 2012)
 */

import edu.berkeley.nlp.coref.Mention
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import org.jobimtext.coref.berkeley.bansalklein.provider.{FeatureProvider, SimpleDatabaseFeatureProvider, SimpleStoreFeatureProvider}

/*
 * This feature type returns the log-binned normalized co-occurrence count (sum of various wildcard-based counts) of
 * the 2 head-words.
 * See Section 3.1 of ACL2012 paper
 */
object GeneralCoOccurrence {
  val FeatureFileOption = "featureFile"
  var provider: FeatureProvider = null

  def getFeature(config: CorefSystemConfiguration, m1: Mention, m2: Mention): String = {
    val h1 = m1.headString
    val h2 = m2.headString
    val path = config.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("GeneralCoOccurence feature used without using precomputed " +
      "features")

    if (provider == null) provider = if (path.endsWith(".db")) new SimpleDatabaseFeatureProvider(path, "bkCoOccurrence") else new SimpleStoreFeatureProvider(path, HeadPairFeatureStore.CoOccurrenceFeatureIdx)

    provider.getFeature(h1, h2) match {
      case None => throw new IllegalStateException(
        s"Precomputed features for co occurrence did not contain pair $h1 $h2")
      case Some(featureValue) => featureValue
    }
  }
}