package edu.berkeley.nlp.coref.config

import edu.berkeley.nlp.coref.ConjType
import org.jobimtext.coref.berkeley.ThesaurusCollection

import scala.beans.BeanProperty

/**
 * CorefSystemConfiguration that provides reasonable default values for different types of actions.
 *
 * @author Tim Feuerbach
 */
abstract class DefaultCorefSystemConfiguration extends CorefSystemConfiguration {
  @BeanProperty var thesaurusCollection: Option[ThesaurusCollection] = None
  @BeanProperty var decodeType: String = "basic"
  @BeanProperty var dtStatistics: Boolean = false
  @BeanProperty var chimergeIntervalsFile: String = "conf/chimerge.properties"
  @BeanProperty var discretizeIntervalFactor: Double = 0.1
  @BeanProperty var inferenceType: InferenceType = InferenceType.PAIRWISE
  @BeanProperty var printSigSuffStats: Boolean = false
  @BeanProperty var analysesToPrint: String = ""
  @BeanProperty var conjType: ConjType = ConjType.CANONICAL
  @BeanProperty var binaryLogThreshold: Double = 0.0
  @BeanProperty var projDefaultWeights: String = "agreeheavy"
  @BeanProperty var lossFcnSecondPass: String = "customLoss-0.1-3-1"
  @BeanProperty var cheatingDomainSize: Int = 5
  @BeanProperty var useGoldMentions: Boolean = false
  @BeanProperty var lossFcn: String = "customLoss-0.1-3-1"
  @BeanProperty var pruningStrategy: String = "distance:10000:5000"
  @BeanProperty var dtConjType: ConjType = ConjType.CANONICAL
  @BeanProperty var dtRemoveIncompatibleTerms: Boolean = false
  @BeanProperty var pruningStrategySecondPass: String = "c2flogratio:2"
  @BeanProperty var numCheatingProperties: Int = 3
  @BeanProperty var trainOnGold: Boolean = false
  @BeanProperty var includeAppositives: Boolean = true
  @BeanProperty var binaryClusterType: String = "TRANSITIVE_CLOSURE"
  @BeanProperty var pairwiseFeatsSecondPass: String = ""
  @BeanProperty var conjTypeSecondPass: ConjType = ConjType.CANONICAL
  @BeanProperty var rahmanTrainType: String = "goldusepred"
  @BeanProperty var binaryNegateThreshold: Boolean = false
  @BeanProperty var phi: Boolean = false
  @BeanProperty var lexicalFeatCutoff: Int = 20
  @BeanProperty var cheat: Boolean = false
  @BeanProperty var phiClusterFeatures: String = ""
  @BeanProperty var eta: Double = 1.0
  @BeanProperty var dtUseCache: Boolean = true
  @BeanProperty var numItrsSecondPass: Int = 20
  @BeanProperty var numItrs: Int = 20
  @BeanProperty var dtRemoveIncompatibleTermsK: Int = 3
  @BeanProperty var reg: Double = 0.001
  @BeanProperty var usePOSForNumberCommon: Boolean = false
  @BeanProperty var useNer: Boolean = true
  @BeanProperty var clusterFeats: String = ""
  @BeanProperty var removeSingletons: Boolean = true
  @BeanProperty var conllOutputDir: String = ""
  @BeanProperty var pairwiseFeats: String = ""
  @BeanProperty var binaryNegativeClassWeight: Double = 1.0
  @BeanProperty var corefClusters: Int = 4
}
