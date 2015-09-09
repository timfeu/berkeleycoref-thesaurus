package org.jobimtext.coref.berkeley.uima;

import edu.berkeley.nlp.coref.ConjType;
import edu.berkeley.nlp.coref.config.InferenceType;

/**
 * Holds the name of parameters and default values for the {@link org.jobimtext.coref.berkeley.uima
 * .CoreferenceAnalysisEngine}.
 *
 * @author Tim Feuerbach
 */
public class ConfigurationParameters {
    public static final String DT_CONF_PATH_PARAM = "dtConfPath";
    public static final String DT_CONF_PATH_DEFAULT = "conf/dt.xml";

    public static final String DECODE_TYPE_PARAM = "decodeType";
    public static final String DECODE_TYPE_DEFAULT = "basic";

    public static final String CHIMERGE_INTERVALS_FILE_PARAM = "chimergeIntervalsFile";
    public static final String CHIMERGE_INTERVALS_FILE_DEFAULT = "conf/chimerge.properties";

    public static final String DISCRETIZE_INTERVAL_FACTOR_PARAM = "discretizeIntervalFactor";
    public static final String DISCRETIZE_INTERVAL_FACTOR_DEFAULT = "0.1";

    public static final String INFERENCE_TYPE_PARAM = "inferenceType";
    public static final String INFERENCE_TYPE_DEFAULT = "PAIRWISE";

    public static final String PRINT_SIG_SUFF_STATS_PARAM = "printSigSuffStats";
    public static final String PRINT_SIG_SUFF_STATS_DEFAULT = "false";

    public static final String ANALYSES_TO_PRINT_PARAM = "analysesToPrint";
    public static final String ANALYSES_TO_PRINT_DEFAULT = "";

    public static final String CONJ_TYPE_PARAM = "conjType";
    public static final String CONJ_TYPE_DEFAULT = "CANONICAL";

    public static final String BINARY_LOG_THRESHOLD_PARAM = "binaryLogThreshold";
    public static final String BINARY_LOG_THRESHOLD_DEFAULT = "0.0";

    public static final String PROJ_DEFAULT_WEIGHTS_PARAM = "projDefaultWeights";
    public static final String PROJ_DEFAULT_WEIGHTS_DEFAULT = "agreeheavy";

    public static final String LOSS_FCN_SECOND_PASS_PARAM = "lossFcnSecondPass";
    public static final String LOSS_FCN_SECOND_PASS_DEFAULT = "customLoss-0.1-3-1";

    public static final String CHEATING_DOMAIN_SIZE_PARAM = "cheatingDomainSize";
    public static final String CHEATING_DOMAIN_SIZE_DEFAULT = "5";

    public static final String USE_GOLD_MENTIONS_PARAM = "useGoldMentions";
    public static final String USE_GOLD_MENTIONS_DEFAULT = "false";

    public static final String LOSS_FCN_PARAM = "lossFcn";
    public static final String LOSS_FCN_DEFAULT = "customLoss-0.1-3-1";

    public static final String PRUNING_STRATEGY_PARAM = "pruningStrategy";
    public static final String PRUNING_STRATEGY_DEFAULT = "distance:10000:5000";

    public static final String DT_CONJ_TYPE_PARAM = "dtConjType";
    public static final String DT_CONJ_TYPE_DEFAULT = CONJ_TYPE_DEFAULT;

    public static final String DT_REMOVE_INCOMPATIBLE_TERMS_PARAM = "dtRemoveIncompatibleTerms";
    public static final String DT_REMOVE_INCOMPATIBLE_TERMS_DEFAULT = "false";

    public static final String PRUNING_STRATEGY_SECOND_PASS_PARAM = "pruningStrategySecondPass";
    public static final String PRUNING_STRATEGY_SECOND_PASS_DEFAULT = "c2flogratio:2";

    public static final String NUM_CHEATING_PROPERTIES_PARAM = "numCheatingProperties";
    public static final String NUM_CHEATING_PROPERTIES_DEFAULT = "3";

    public static final String TRAIN_ON_GOLD_PARAM = "trainOnGold";
    public static final String TRAIN_ON_GOLD_DEFAULT = "false";

    public static final String INCLUDE_APPOSITIVES_PARAM = "includeAppositives";
    public static final String INCLUDE_APPOSITIVES_DEFAULT = "true";

    public static final String BINARY_CLUSTER_TYPE_PARAM = "binaryClusterType";
    public static final String BINARY_CLUSTER_TYPE_DEFAULT = "TRANSITIVE_CLOSURE";

    public static final String PAIRWISE_FEATS_SECOND_PASS_PARAM = "pairwiseFeatsSecondPass";
    public static final String PAIRWISE_FEATS_SECOND_PASS_DEFAULT = "";

    public static final String CONJ_TYPE_SECOND_PASS_PARAM = "conjTypeSecondPass";
    public static final String CONJ_TYPE_SECOND_PASS_DEFAULT = "CANONICAL";

    public static final String RAHMAN_TRAIN_TYPE_PARAM = "rahmanTrainType";
    public static final String RAHMAN_TRAIN_TYPE_DEFAULT = "goldusepred";

    public static final String BINARY_NEGATE_THRESHOLD_PARAM = "binaryNegateThreshold";
    public static final String BINARY_NEGATE_THRESHOLD_DEFAULT = "false";

    public static final String PHI_PARAM = "phi";
    public static final String PHI_DEFAULT = "false";

    public static final String LEXICAL_FEATS_CUTOFF_PARAM = "lexicalFeatsCutoff";
    public static final String LEXICAL_FEATS_CUTOFF_DEFAULT = "20";

    public static final String CHEAT_PARAM = "cheat";
    public static final String CHEAT_DEFAULT = "false";

    public static final String PHI_CLUSTER_FEATURES_PARAM = "phiClusterFeatures";
    public static final String PHI_CLUSTER_FEATURES_DEFAULT = "";

    public static final String ETA_PARAM = "eta";
    public static final String ETA_DEFAULT = "1.0";

    public static final String DT_USE_CACHE_PARAM = "dtUseCache";
    public static final String DT_USE_CACHE_DEFAULT = "true";

    public static final String NUM_ITRS_PARAM = "numItrs";
    public static final String NUM_ITRS_DEFAULT = "20";

    public static final String NUM_ITRS_SECOND_PASS_PARAM = "numItrsSecondPass";
    public static final String NUM_ITRS_SECOND_PASS_DEFAULT = NUM_ITRS_DEFAULT;

    public static final String DT_REMOVE_INCOMPATIBLE_TERMS_K_PARAM = "dtRemoveIncompatibleTermsK";
    public static final String DT_REMOVE_INCOMPATIBLE_TERMS_K_DEFAULT = "3";

    public static final String REG_PARAM = "reg";
    public static final String REG_DEFAULT = "0.001";

    public static final String USE_POS_FOR_NUMBER_COMMON_PARAM = "usePOSForNumberCommon";
    public static final String USE_POS_FOR_NUMBER_COMMON_DEFAULT = "false";

    public static final String USE_NER_PARAM = "useNer";
    public static final String USE_NER_DEFAULT = "true";

    public static final String CLUSTER_FEATS_PARAM = "clusterFeats";
    public static final String CLUSTER_FEATS_DEFAULT = "";

    public static final String REMOVE_SINGLETONS_PARAM = "removeSingletons";
    public static final String REMOVE_SINGLETONS_DEFAULT = "true";

    public static final String CONLL_OUTPUT_DIR_PARAM = "conllOutputDir";
    public static final String CONLL_OUTPUT_DIR_DEFAULT = "";

    public static final String PAIRWISE_FEATS_PARAM = "pairwiseFeats";
    public static final String PAIRWISE_FEATS_DEFAULT = "FINAL";

    public static final String BINARY_NEGATIVE_CLASS_WEIGHT_PARAM = "binaryNegativeClassWeight";
    public static final String BINARY_NEGATIVE_CLASS_WEIGHT_DEFAULT = "1.0";

    public static final String COREF_CLUSTERS_PARAM = "corefClusters";
    public static final String COREF_CLUSTERS_DEFAULT = "4";

    public static final String LANGUAGE_PARAM = "language";
    public static final String LANGUAGE_DEFAULT = "ENGLISH";

    public static final String STOPWORD_LIST_PATH_PARAM = "stopwordList";
    public static final String STOPWORD_LIST_PATH_DEFAULT = "data/stopwords.txt";

    public static final String USE_DUMMY_THESAURUS_PARAM = "useDummyThesaurus";
    public static final String USE_DUMMY_THESAURUS_DEFAULT = "false";

    public static final String MODEL_LOCATION_PARAM = "modelLocation";
    public static final String MODEL_IS_GZIPPED_PARAM = "modelIsGzipped";
    public static final String MODEL_IS_GZIPPED_DEFAULT = "true";

    public static final String MODEL_VARIANT_PARAM = "modelVariant";

    public static final String NUMBER_GENDER_DATA_PARAM = "numberGenderDataLocation";
    public static final String NUMBER_GENDER_DATA_IS_GZIPPED_PARAM = "ngDataIsGzipped";
    public static final String NUMBER_GENDER_DATA_IS_GZIPPED_DEFAULT = "true";
}
