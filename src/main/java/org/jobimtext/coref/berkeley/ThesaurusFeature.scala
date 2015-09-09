package org.jobimtext.coref.berkeley

/**
 * Single feature to be used by the [[edu.berkeley.nlp.coref.PairwiseIndexingFeaturizerJoint]].
 *
 * @author Tim Feuerbach
 */
@SerialVersionUID(1L) case class ThesaurusFeature(thesaurusId: String, featureName: String, options: Map[String, String]) extends Serializable {
  override def toString = "ThesaurusFeature(id: " + thesaurusId + ", feature: " + featureName + ", " +
    "options: " + options.toString
}

@SerialVersionUID(1L)
object ThesaurusFeature {
  val TargetOption = "target"
  val ExpandOption = "expand"
  val OnlyOpenOption = "onlyOpen"
  val OnlyClosedOption = "onlyClosed"
  val DiscretizeOption = "discretize"
  val RoundOption = "roundFactor"
  val LimitOption = "limit"
  val FilterOption = "filter"
  val lexicalizeOption = "lexicalize"
  val ContextOption = "usePartnerContext"
}
