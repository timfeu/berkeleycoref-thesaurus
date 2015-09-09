package edu.berkeley.nlp.coref.exception

import org.jobimtext.coref.berkeley.ThesaurusFeature

/**
 * Thrown when a thesaurus configuration has not all features that a previously defined model had.
 *
 * @author Tim Feuerbach
 */
class ThesaurusFeaturesMissingException(val missingFeatures: Seq[ThesaurusFeature]) extends RuntimeException(ThesaurusFeatureMissingException.createMessage(missingFeatures)) {

}

object ThesaurusFeatureMissingException {
  def createMessage(missingFeatures: Seq[ThesaurusFeature]) = {
    val messageBuilder = new StringBuilder

    messageBuilder.append("Thesaurus configuration is missing some features:\n")

    missingFeatures.foreach{feature =>
      messageBuilder.append(feature.featureName)
      messageBuilder.append(" with options ")
      messageBuilder.append(feature.options.mkString)
      messageBuilder.append(" for thesaurus ")
      messageBuilder.append(feature.thesaurusId)
      messageBuilder.append("\n")
    }

    messageBuilder.toString()
  }
}
