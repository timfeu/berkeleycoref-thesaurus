package edu.berkeley.nlp.coref.config

import edu.berkeley.nlp.coref.{NumberGenderComputer, ConjType}
import edu.berkeley.nlp.coref.lang.CorefLanguagePack
import org.jobimtext.coref.berkeley.ThesaurusCollection

import scala.annotation.meta.{setter, getter}
import scala.beans.BeanProperty

/**
 * Holds the configuration information necessary to run the coreference system, but not paths to training or testing
 * data. You should use one of the default implementations for training, testing and predictions unless there are
 * special requirements necessary.
 *
 * @author Tim Feuerbach
 */
trait CorefSystemConfiguration extends Cloneable {


  /**
   * Provides language specific methods.
   */
  @BeanProperty var languagePack: CorefLanguagePack

  /**
   * Accesses number/gender data provided by Bergsma and Lin
   */
  @BeanProperty var numberGenderComputer: NumberGenderComputer

  /**
   * Holds distributional thesauri used during featurization.
   */
  @BeanProperty var thesaurusCollection: Option[ThesaurusCollection]

  /**
   * Whether the POS tag should be used to determine the number of common nouns.
   */
  @BeanProperty var usePOSForNumberCommon: Boolean

  /**
   * Use NER in the system or ignore it?
   */
  @BeanProperty var useNer: Boolean

  /**
   * True if we should train on the documents with gold annotations, false if we should use auto annotations
   */
  @BeanProperty var trainOnGold: Boolean

  /**
   * Whether gold mention boundaries should be used (train and/or test)
   */
  @BeanProperty var useGoldMentions: Boolean

  /**
   * Can toggle whether written output is filtered for singletons or not
   */
  @BeanProperty var removeSingletons: Boolean

  /**
   * Directory to write output CoNLL files to when using the scorer. If blank, uses the default temp directory and
   * deletes them after. You might want this because calling the scorer forks the process and may give an
   * out-of-memory error, so this is some insurance that at least you'll have your output.
   */
  @BeanProperty var conllOutputDir: String

  /**
   * Use appositives as mentions as well? Disabling this will decrease the number of recalled mentions, since some
   * appositives may be incorrectly marked by the parser.
   */
  @BeanProperty var includeAppositives: Boolean

  /**
   * Print per-document scores for bootstrap significance testing
   */
  @BeanProperty var printSigSuffStats: Boolean

  /**
   * Whether distributed thesaurus information should be cached. The cache will be emptied after each processed
   * document.
   */
  @BeanProperty var dtUseCache: Boolean

  /**
   * Entity model settings: Use cheating clusters?
   */
  @BeanProperty var cheat: Boolean

  /**
   * Entity model settings: Number of cheating clusters
   */
  @BeanProperty var numCheatingProperties: Int

  /**
   * Entity model settings: Domain size for each cheating cluster
   */
  @BeanProperty var cheatingDomainSize: Int

  /**
   * Entity model settings: Use phi-feature based clusters?
   */
  @BeanProperty var phi: Boolean

  /**
   * Entity model settings: Which phi cluster features to include (numb, gend, or nert)
   */
  @BeanProperty var phiClusterFeatures: String

  /**
   * eta for Adagrad
   */
  @BeanProperty var eta: Double

  /**
   * Regularization constant (might be lambda or c depending on which algorithm is used)
   */
  @BeanProperty var reg: Double

  /**
   * Loss function to use
   */
  @BeanProperty var lossFcn: String

  /**
   * Loss function to use for second pass
   */
  @BeanProperty var lossFcnSecondPass: String

  /**
   * Number of training iterations
   */
  @BeanProperty var numItrs: Int

  /**
   * Number of training iterations during the second pass
   */
  @BeanProperty var numItrsSecondPass: Int

  /**
   * Pruning strategy for first pass
   */
  @BeanProperty var pruningStrategy: String

  /**
   * Pruning strategy for second pass
   */
  @BeanProperty var pruningStrategySecondPass: String

  /**
   * Inference type for entity models
   */
  @BeanProperty var inferenceType: InferenceType

  /**
   * Basic features to use during main pass. Default (empty) contains only surface features. Use "+FINAL" for the
   * Berkeley FINAL system. Thesaurus features are configured
   * individually using a configuration file.
   */
  @BeanProperty var pairwiseFeats: String

  /**
   * Basic features to use during the second pass (only entity models). Default (empty) contains only surface
   * features. Use "+FINAL" for the Berkeley FINAL system. Thesaurus features are configured individually using a
   * configuration file.
   */
  @BeanProperty var pairwiseFeatsSecondPass: String

  /**
   * Type of feature conjunctions.
   */
  @BeanProperty var conjType: ConjType

  /**
   * Type of feature cojunctions in the second pass (only entity models).
   */
  @BeanProperty var conjTypeSecondPass: ConjType

  /**
   * Conjunction type for distributional thesaurus features
   */
  @BeanProperty var dtConjType: ConjType

  /**
   * Cutoff below which lexical features fire POS tags instead
   */
  @BeanProperty var lexicalFeatCutoff: Int

  /**
   * Factor for the maximum value to calculate the interval steps for discretization of integer values. The default
   * value of 0.1, for example, would yield steps of 10 if the maximum value is 100. The resulting step will be
   * floored. A value of 0 or less results in no discretization (the value itself will be taken),
   * which can be reasonable if the maximum value is not that big, though it requires a large training set to yield
   * useful weights. Percentage values will always be discretized by rounding to the first digit.
   */
  @BeanProperty var discretizeIntervalFactor: Double

  /**
   * If set to true, all incompatible terms will be removed from a thesaurus' prior expansion. Requires that
   * an antonym database has been set up for all thesauri.
   */
  @BeanProperty var dtRemoveIncompatibleTerms: Boolean

  /**
   * Remove incompatible terms if the database lists them at least k+1 times. Has no effect if neither
   * dtRemoveIncompatibleTerms is used nor the clashing attributes feature.
   */
  @BeanProperty var dtRemoveIncompatibleTermsK: Int

  /**
   * Location of the precomputed chimerge intervals to use if discretization type "intervalFile" is used.
   *
   * TODO allow interval file for as much expansion types as possible
   */
  @BeanProperty var chimergeIntervalsFile: String

  /**
   * Decode with max or with left-to-right marginalization?
   */
  @BeanProperty var decodeType: String

  /**
   * TODO find out what this does
   */
  @BeanProperty var binaryLogThreshold: Double

  /**
   * TODO find out what this does
   */
  @BeanProperty var binaryNegateThreshold: Boolean

  /**
   * TODO document
   */
  @BeanProperty var binaryClusterType: String

  /**
   * TODO find out what this does
   */
  @BeanProperty var binaryNegativeClassWeight: Double

  /**
   * What kind of loopy featurization to use
   */
  @BeanProperty var clusterFeats: String

  /**
   * TODO doc
   */
  @BeanProperty var rahmanTrainType: String

  /**
   * What to regularize the projected default weights towards
   */
  @BeanProperty var projDefaultWeights: String

  /**
   * Number of coref clusters to use (may be reduced from number of total clusters)
   */
  @BeanProperty var corefClusters: Int

  /**
   * Analyses to print: +purity, +discourse, +categorizer, +mistakes
   */
  @BeanProperty var analysesToPrint: String

  /**
   * Collect additional thesaurus statistics (consumes a lot of memory)
   */
  @BeanProperty var dtStatistics: Boolean

  private val additionalProperties = scala.collection.mutable.Map.empty[(Class[_], String), Any]

  /**
   * Returns an additional property of this configuration. Additional properties are used by subsystems that may
   * be switched out.
   *
   * @param scope the subsystem that requires this additional property.
   * @param key the property's name
   *
   * @return The stored value
   */
  def getAdditionalProperty(scope: Class[_], key: String) = additionalProperties((scope, key))

  /**
   * Returns an additional property of this configuration. Additional properties are used by subsystems that may
   * be switched out.
   *
   * @param scope the subsystem that requires this additional property.
   * @param key the property's name
   * @param default the default value to return if the property has not been set
   *
   * @return The stored value
   */
  def getAdditionalProperty(scope: Class[_], key: String, default: Any) = additionalProperties.getOrElse((scope,
    key), default)

  /**
   * Stores an additional property of this configuration. Additional properties are used by subsystems that may
   * be switched out.
   *
   * @param scope the subsystem that requires this additional property.
   * @param key the property's name
   * @param value The value to store
   */
  def setAdditionalProperty(scope: Class[_], key: String, value: AnyRef) {
    additionalProperties((scope, key)) = value
  }

  override def clone(): CorefSystemConfiguration = super.clone().asInstanceOf[CorefSystemConfiguration]
}
