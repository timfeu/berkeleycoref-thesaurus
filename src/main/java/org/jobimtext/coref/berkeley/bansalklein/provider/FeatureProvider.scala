package org.jobimtext.coref.berkeley.bansalklein.provider

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration

trait FeatureProvider  {
  def getFeature(h1: String, h2: String): Option[String]
}
