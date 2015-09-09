package org.jobimtext.coref.berkeley.bansalklein

import edu.berkeley.nlp.coref.{Mention, ConjType, DocumentGraph, PairwiseIndexingFeaturizerJoint}

import scala.collection.mutable.ArrayBuffer

class BansalKleinFeaturizer private(val featurizer: PairwiseIndexingFeaturizerJoint) {
  def featurizeIndexStandard(docGraph: DocumentGraph, currMent: Mention, antecedentMent: Mention, startingNew: Boolean,
                             addFeatureShortcut: (String) => Unit): Unit = {
    // Pairwise features
    if (!startingNew) {
      // 3.1: co-occurrence
      if (featurizer.featsToUse.contains("+bkCoOccurrence")) {
        addFeatureShortcut("BKGeneralCoOcurrenceCurrAnt=" + GeneralCoOccurrence.getFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
        addFeatureShortcut("BKGeneralCoOccurrenceAntCurr=" + GeneralCoOccurrence.getFeature(featurizer.mentionPropertyComputer.config, antecedentMent, currMent))
      }

      // 3.2: Hearst patterns
      if (featurizer.featsToUse.contains("+bkHearst")) {
        addFeatureShortcut("BKHearstCurrAnt=" + HearstPattern.getFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
        addFeatureShortcut("BKHearstAntCurr=" + HearstPattern.getFeature(featurizer.mentionPropertyComputer.config, antecedentMent, currMent));
      }

      // 3.3: entity seeds
      if (featurizer.featsToUse.contains("+bkEntityMatch")) {
        addFeatureShortcut("BKEntityMatch=" + Entity.getSeedMatchFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
      }

      if (featurizer.featsToUse.contains("+bkEntityDominantPos")) {
        addFeatureShortcut("BKEntityDominantPos=" + Entity.getDominantPosFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
      }

      // 3.4: cluster features /symmetric)
      if (featurizer.featsToUse.contains("+bkCluster")) {
        addFeatureShortcut("BKClusterMatch=" + Cluster.getFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent));
      }

      // 3.5: pronoun context
      if (featurizer.featsToUse.contains("+bkPronounContext") && PronounContext.isFeatureApplicable(currMent, antecedentMent)) {
        if (featurizer.featsToUse.contains("+bkPronounContextR1")) addFeatureShortcut("BKPronounContextR1=" + PronounContext.getR1Feature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
        if (featurizer.featsToUse.contains("+bkPronounContextR2")) addFeatureShortcut("BKPronounContextR2=" + PronounContext.getR2Feature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
        if (featurizer.featsToUse.contains("+bkPronounContextR1Gap")) addFeatureShortcut("BKPronounContextR1Gap=" + PronounContext.getR1GapFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
      }

      // not a B&K feature: patterns of incompatibility (a symmetric measure)
      if (featurizer.featsToUse.contains("+incompatibilityWeb")) {
        addFeatureShortcut("incompatibilityWeb=" + Incompatibility.getFeature(featurizer.mentionPropertyComputer.config, currMent, antecedentMent))
      }
    }
  }
}

object BansalKleinFeaturizer {
  def apply(featurizer: PairwiseIndexingFeaturizerJoint): BansalKleinFeaturizer = {
    new BansalKleinFeaturizer(featurizer)
  }
}
