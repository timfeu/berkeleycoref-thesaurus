package org.jobimtext.coref.berkeley.uima;

import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ModelProviderBase;
import edu.berkeley.nlp.coref.ConjType;
import edu.berkeley.nlp.coref.CorefSystem;
import edu.berkeley.nlp.coref.ModelContainer;
import edu.berkeley.nlp.coref.NumberGenderComputer;
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration;
import edu.berkeley.nlp.coref.config.InferenceType;
import edu.berkeley.nlp.coref.config.PredictionCorefSystemConfiguration;
import edu.berkeley.nlp.coref.lang.Language;
import edu.berkeley.nlp.coref.lang.LanguagePackFactory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jobimtext.coref.berkeley.DistributionalThesaurusComputer;
import org.jobimtext.coref.berkeley.ThesaurusLoader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static org.jobimtext.coref.berkeley.uima.ConfigurationParameters.*;

/**
 * Apache UIMA analysis engine that adds coreference resolution information to a CAS object by using the Berkeley
 * Coreference Resolution System.
 * <p>
 * TODO support entity level models
 *
 * @author Tim Feuerbach
 */
@TypeCapability(
        inputs = {
                "de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity",
                "de.tudarmstadt.ukp.dkpro.core.api.syntax.type.constituent.Constituent",
                "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
                "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
                "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence"},
        outputs = {
                "de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceChain",
                "de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceLink"})
public class CoreferenceAnalysisEngine extends JCasAnnotator_ImplBase {

    /**
     * Path to the XML file describing the thesaurus database connection details and enabled thesaurus features.
     * A separate connection is opened for each thesaurus.
     */
    @ConfigurationParameter(name = ConfigurationParameters.DT_CONF_PATH_PARAM, defaultValue = ConfigurationParameters
            .DT_CONF_PATH_DEFAULT, mandatory = true)
    private String dtConfPath;

    /**
     * The features to use during the normal pass (the only pass if the entity level model is not applied). The
     * features don't have to be separated by a character. Write "FINAL" for the Berkeley Final System.
     * <p>
     * Distributional thesaurus features are configured separately per thesaurus in the thesaurus configuration file.
     * See {@link #dtConfPath} for setting the path to the configuration file.
     */
    @ConfigurationParameter(name = PAIRWISE_FEATS_PARAM, defaultValue = PAIRWISE_FEATS_DEFAULT, mandatory = true)
    private String pairwiseFeats;

    /**
     * The language to fall back if the language of the CAS is not supported.
     */
    @ConfigurationParameter(name = LANGUAGE_PARAM, defaultValue = LANGUAGE_DEFAULT, mandatory = true)
    private Language language;

    /**
     * TODO doc.
     */
    @ConfigurationParameter(name = DECODE_TYPE_PARAM, defaultValue = DECODE_TYPE_DEFAULT, mandatory = true)
    private String decodeType;

    /**
     * Path to a Java properties file specifying intervals for chimerge discretization.
     * <p>
     * Chimerge discretization is valid for interval-based features. To specify the intervals, add an entry to
     * the properties file with its key being the feature name and its value a semicolon-separated list (no spaces)
     * of cut points, e.g.
     * <p>
     * <code>
     * priorExpansion = -2;-1;0;2;4;5;6;7;8;9;10;11;12;14;83;84;86;153;154;156;157;197;199;200
     * </code>
     * <p>
     * specifies the intervals (∞,-2), [-2, -1), etc.
     */
    @ConfigurationParameter(name = CHIMERGE_INTERVALS_FILE_PARAM, defaultValue = CHIMERGE_INTERVALS_FILE_DEFAULT,
            mandatory = true)
    private String chimergeIntervalsFile;

    /**
     * Factor used to determine the number of intervals and their size for discretization. The factor is multiplied
     * with the number of terms in a prior expansion to get the interval size.
     */
    @ConfigurationParameter(name = DISCRETIZE_INTERVAL_FACTOR_PARAM,
            defaultValue = DISCRETIZE_INTERVAL_FACTOR_DEFAULT, mandatory = true)
    private double discretizeIntervalFactor;

    /**
     * Type of inference to use. PAIRWISE is the standard model; the other are only useful in case of the entity
     * level model.
     */
    @ConfigurationParameter(name = INFERENCE_TYPE_PARAM, defaultValue = INFERENCE_TYPE_DEFAULT, mandatory = true)
    private InferenceType inferenceType;

    /**
     * Feature cojunctions type.
     */
    @ConfigurationParameter(name = CONJ_TYPE_PARAM, defaultValue = CONJ_TYPE_DEFAULT, mandatory = true)
    private ConjType conjType;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = BINARY_LOG_THRESHOLD_PARAM, defaultValue = BINARY_LOG_THRESHOLD_DEFAULT,
            mandatory = true)
    private double binaryLogThreshold;

    /**
     * TODO doc (entity model)
     */
    @ConfigurationParameter(name = PROJ_DEFAULT_WEIGHTS_PARAM, defaultValue = PROJ_DEFAULT_WEIGHTS_DEFAULT,
            mandatory = true)
    private String projDefaultWeights;

    /**
     * The pruning strategy to apply.
     * <p>
     * TODO doc
     */
    @ConfigurationParameter(name = PRUNING_STRATEGY_PARAM, defaultValue = PRUNING_STRATEGY_DEFAULT, mandatory = true)
    private String pruningStrategy;

    /**
     * The feature conjunction type to use for thesaurus features.
     */
    @ConfigurationParameter(name = DT_CONJ_TYPE_PARAM, defaultValue = DT_CONJ_TYPE_DEFAULT, mandatory = true)
    private ConjType dtConjType;


    /**
     * Whether to remove terms reported as incompatible by the antonym database from prior expansions if they exceed
     * the threshold specified by the value set in the configuration parameter "{@value org.jobimtext.coref.berkeley
     * .uima.ConfigurationParameters#DT_REMOVE_INCOMPATIBLE_TERMS_PARAM}". Note that prior expansions may be used by
     * various features, not only the prior feature; the terms will be removed from all of them.
     * <p>
     * The antonym database connection has to be set up in the thesaurus configuration file.
     */
    @ConfigurationParameter(name = DT_REMOVE_INCOMPATIBLE_TERMS_PARAM,
            defaultValue = DT_REMOVE_INCOMPATIBLE_TERMS_DEFAULT, mandatory = true)
    private boolean dtRemoveIncompatibleTerms;

    /**
     * The pruning strategy to employ during the second pass. Only required if the two-pass (entity level) model is
     * used.
     * <p>
     * TODO doc
     */
    @ConfigurationParameter(name = PRUNING_STRATEGY_SECOND_PASS_PARAM,
            defaultValue = PRUNING_STRATEGY_SECOND_PASS_DEFAULT, mandatory = true)
    private String pruningStrategySecondPass;

    /**
     * Number of cheating clusters. Only used in entity level model ad if cheating is enabled.
     */
    @ConfigurationParameter(name = NUM_CHEATING_PROPERTIES_PARAM, defaultValue = NUM_CHEATING_PROPERTIES_DEFAULT,
            mandatory = true)
    private int numCheatingProperties;

    /**
     * Whether appositives should be added as predicted mentions as well and not only the NP they are contained in.
     * While they are non-referring if standard identy coreference resolution is performed,
     * the parser may incorrectly deem NPs as appositives. This value is set to true by default to achieve maximum
     * recall on predicted mentions.
     */
    @ConfigurationParameter(name = INCLUDE_APPOSITIVES_PARAM, defaultValue = INCLUDE_APPOSITIVES_DEFAULT,
            mandatory = true)
    private boolean includeAppositives;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = BINARY_CLUSTER_TYPE_PARAM, defaultValue = BINARY_CLUSTER_TYPE_DEFAULT,
            mandatory = true)
    private String binaryClusterType;

    /**
     * The features to use during the second pass, which is only performed in an entity level model.
     */
    @ConfigurationParameter(name = PAIRWISE_FEATS_SECOND_PASS_PARAM,
            defaultValue = PAIRWISE_FEATS_SECOND_PASS_DEFAULT, mandatory = true)
    private String pairwiseFeatsSecondPass;

    /**
     * Feature cojunction type to use during the second pass of entity level models.
     */
    @ConfigurationParameter(name = CONJ_TYPE_SECOND_PASS_PARAM, defaultValue = CONJ_TYPE_SECOND_PASS_DEFAULT,
            mandatory = true)
    private ConjType conjTypeSecondPass;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = RAHMAN_TRAIN_TYPE_PARAM, defaultValue = RAHMAN_TRAIN_TYPE_DEFAULT, mandatory = true)
    private String rahmanTrainType;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = BINARY_NEGATE_THRESHOLD_PARAM, defaultValue = BINARY_NEGATE_THRESHOLD_DEFAULT,
            mandatory = true)
    private boolean binaryNegateThreshold;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = PHI_PARAM, defaultValue = PHI_DEFAULT)
    private boolean phi;

    /**
     * If a word was seen less times than this cutoff, the lexical feat for it will be its POS tag instead.
     */
    @ConfigurationParameter(name = LEXICAL_FEATS_CUTOFF_PARAM, defaultValue = LEXICAL_FEATS_CUTOFF_DEFAULT,
            mandatory = true)
    private int lexicalFeatsCutoff;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = CHEAT_PARAM, defaultValue = CHEAT_DEFAULT, mandatory = true)
    private boolean cheat;


    /**
     * TODO doc
     */
    @ConfigurationParameter(name = PHI_CLUSTER_FEATURES_PARAM, defaultValue = PHI_CLUSTER_FEATURES_DEFAULT,
            mandatory = true)
    private String phiClusterFeatures;

    /**
     * Whether to cache expansions per document. Disabling this value is not recommended; the memory used by the
     * cache is admissible, provided that the document to featurize is not unusually large.
     */
    @ConfigurationParameter(name = DT_USE_CACHE_PARAM, defaultValue = DT_USE_CACHE_DEFAULT, mandatory = true)
    private boolean dtUseCache;

    /**
     * Threshold of occurrences in the antonym database above which incompatible terms are removed from the prior
     * expansion if "{@value org.jobimtext.coref.berkeley.uima
     * .ConfigurationParameters#DT_REMOVE_INCOMPATIBLE_TERMS_PARAM}" is enabled or a corresponding feature is used.
     */
    @ConfigurationParameter(name = DT_REMOVE_INCOMPATIBLE_TERMS_K_PARAM,
            defaultValue = DT_REMOVE_INCOMPATIBLE_TERMS_K_DEFAULT, mandatory = true)
    private int dtRemoveIncompatibleTermsK;

    /**
     * Whether the POS tag should be used to determine the number of common nouns. The standard setting uses the
     * number gender data to determine the number and falls back to ??? otherwise.
     * <p>
     * TODO fall back to?
     */
    @ConfigurationParameter(name = USE_POS_FOR_NUMBER_COMMON_PARAM, defaultValue = USE_POS_FOR_NUMBER_COMMON_DEFAULT,
            mandatory = true)
    private boolean usePOSForNumberCommon;

    /**
     * Whether to add all named entities as predicted mentions. Named entities are sometimes of higher quality and
     * provide useful clues about mention boundaries.
     */
    @ConfigurationParameter(name = USE_NER_PARAM, defaultValue = USE_NER_DEFAULT, mandatory = true)
    private boolean useNer;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = CLUSTER_FEATS_PARAM, defaultValue = CLUSTER_FEATS_DEFAULT, mandatory = true)
    private String clusterFeats;

    /**
     * Whether to remove singletons (entities with only one mention) from the final output. If set to false,
     * every predicted mention that is found not to be coreferent will form its own entity. Usually you do not want
     * this. Since the Berkeley system does not distinguish between referring and non-referring mentions,
     * there is not much information to gain by keeping singletons.
     */
    @ConfigurationParameter(name = REMOVE_SINGLETONS_PARAM, defaultValue = REMOVE_SINGLETONS_DEFAULT, mandatory = true)
    private boolean removeSingletons;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = BINARY_NEGATIVE_CLASS_WEIGHT_PARAM,
            defaultValue = BINARY_NEGATIVE_CLASS_WEIGHT_DEFAULT, mandatory = true)
    private double binaryNegativeClassWeight;

    /**
     * TODO doc
     */
    @ConfigurationParameter(name = COREF_CLUSTERS_PARAM, defaultValue = COREF_CLUSTERS_DEFAULT, mandatory = true)
    private int corefClusters;

    /*
     * Not used as values of CorefSystemConfiguration
     */

    /**
     * Some thesauri (e.g. N-gram) have too many terms sharing the same feature to do context expansion in reasonable
     * time. They remove context features consisting solely of stop words from the expansion to speed up the
     * computation. The file is a newline-separated list of words. Lines starting with "#" are considered as comments
     * and therefore ignored.
     */
    @ConfigurationParameter(name = STOPWORD_LIST_PATH_PARAM, defaultValue = STOPWORD_LIST_PATH_DEFAULT,
            mandatory = true)
    private String stopwordListPath;

    /**
     * Whether to use a dummy thesaurus implementation, i.e. a thesaurus that does not access a real thesaurus but
     * yields empty expansions. Setting this option is <b>not</b> recommended. If you don't want to use any thesaurus
     * at all, set the parameter "{@value org.jobimtext.coref.berkeley.uima
     * .ConfigurationParameters#DT_CONF_PATH_PARAM}" to a not existing file (e.g. "delete.me").
     * <p>
     * A use case of dummy thesauri for prediction is if you just want to exploit a thesaurus' holing operation in
     * combination with the attribute-centric and prior expansion features, which can already provide a small
     * performance increase. However, the accompanying model should have been trained with a dummy thesaurus as well
     * to obtain the best results in this set-up.
     */
    @ConfigurationParameter(name = USE_DUMMY_THESAURUS_PARAM, defaultValue = USE_DUMMY_THESAURUS_DEFAULT,
            mandatory = true)
    private boolean useDummyThesaurus;

    /*
    Shared Resources
     */

    /**
     * Location of the coreference model to use instead of the automatically loaded
     * model.
     */
    @ConfigurationParameter(name = MODEL_LOCATION_PARAM, mandatory = false)
    private String modelLocation;

    @ConfigurationParameter(name = MODEL_IS_GZIPPED_PARAM, mandatory = true, defaultValue = MODEL_IS_GZIPPED_DEFAULT)
    private boolean modelIsGzipped;

    /**
     * Override the default variant used to locate the model.
     */
    @ConfigurationParameter(name = MODEL_VARIANT_PARAM, mandatory = false)
    private String modelVariant;

    /**
     * Override the default location of the number gender computer.
     */
    @ConfigurationParameter(name = NUMBER_GENDER_DATA_PARAM, mandatory = false)
    private String numberGenderDataLocation;

    @ConfigurationParameter(name = NUMBER_GENDER_DATA_IS_GZIPPED_PARAM, mandatory = true, defaultValue = NUMBER_GENDER_DATA_IS_GZIPPED_DEFAULT)
    private boolean numberGenderDataIsGzipped;

    /*
     * Actual AE implementation (written in Scala)
     */
    private CoreferenceAnalysisEngineImpl engine;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);

        CasConfigurableProviderBase<ModelContainer> modelProvider = new ModelProviderBase<ModelContainer>() {
            {
                setContextObject(CoreferenceAnalysisEngine.this);
                setDefault(ARTIFACT_ID, "org.jobimtext.coref.berkeley.model-${language}-${variant}");
                setDefault(LOCATION, "classpath:/${package}/lib/model-${language}-${variant}.ser.gz");
                setDefault(VARIANT, "final");

                setOverride(LOCATION, modelLocation);
                setOverride(VARIANT, modelVariant);
            }

            @Override
            protected ModelContainer produceResource(InputStream aStream) throws Exception {
                return CorefSystem.loadModelFile(
                        (modelIsGzipped) ? new BufferedInputStream(new GZIPInputStream(aStream)) :
                        aStream);
            }
        };

        CasConfigurableProviderBase<NumberGenderComputer> numberGenderComputerProvider = new ModelProviderBase<NumberGenderComputer>() {
            {
                setContextObject(CoreferenceAnalysisEngine.this);
                setDefault(ARTIFACT_ID, "org.jobimtext.coref.berkeley.ngdata-${language}-default");
                setDefault(LOCATION, "classpath:/${package}/lib/ngdata-${language}-default.data.gz");

                setOverride(LOCATION, numberGenderDataLocation);
            }

            @Override
            protected NumberGenderComputer produceResource(InputStream aStream) throws Exception {
                return NumberGenderComputer.readBergsmaLinData((numberGenderDataIsGzipped) ? new BufferedInputStream(new GZIPInputStream(aStream)) : aStream);
            }
        };

        CorefSystemConfiguration config = createConfiguration();
        engine = new CoreferenceAnalysisEngineImpl(config, modelProvider, numberGenderComputerProvider, getContext().getLogger());
    }

    /**
     * Sets the configuration parameters and loads the thesaurus collection.
     *
     * @return
     */
    private CorefSystemConfiguration createConfiguration() {
        CorefSystemConfiguration config = new PredictionCorefSystemConfiguration(LanguagePackFactory.getLanguagePack
                (language), null);

        config.setAdditionalProperty(DistributionalThesaurusComputer.class, "stopwordListPath", stopwordListPath);

        config.setPairwiseFeats(pairwiseFeats);
        config.setDecodeType(decodeType);
        config.setChimergeIntervalsFile(chimergeIntervalsFile);
        config.setDiscretizeIntervalFactor(discretizeIntervalFactor);
        config.setInferenceType(inferenceType);
        config.setConjType(conjType);
        config.setBinaryLogThreshold(binaryLogThreshold);
        config.setProjDefaultWeights(projDefaultWeights);
        config.setPruningStrategy(pruningStrategy);
        config.setDtConjType(dtConjType);
        config.setDtRemoveIncompatibleTerms(dtRemoveIncompatibleTerms);
        config.setDtRemoveIncompatibleTermsK(dtRemoveIncompatibleTermsK);
        config.setPruningStrategySecondPass(pruningStrategySecondPass);
        config.setNumCheatingProperties(numCheatingProperties);
        config.setIncludeAppositives(includeAppositives);
        config.setCheat(cheat);
        config.setBinaryClusterType(binaryClusterType);
        config.setPairwiseFeatsSecondPass(pairwiseFeatsSecondPass);
        config.setConjTypeSecondPass(conjTypeSecondPass);
        config.setRahmanTrainType(rahmanTrainType);
        config.setBinaryNegateThreshold(binaryNegateThreshold);
        config.setPhi(phi);
        config.setLexicalFeatCutoff(lexicalFeatsCutoff);
        config.setPhiClusterFeatures(phiClusterFeatures);
        config.setDtUseCache(dtUseCache);
        config.setUsePOSForNumberCommon(usePOSForNumberCommon);
        config.setUseNer(useNer);
        config.setClusterFeats(clusterFeats);
        config.setRemoveSingletons(removeSingletons);
        config.setBinaryNegativeClassWeight(binaryNegativeClassWeight);
        config.setCorefClusters(corefClusters);


        // Load thesauri
        config.setThesaurusCollection(ThesaurusLoader.loadThesaurusCollection(new File(dtConfPath), config,
                useDummyThesaurus));

        return config;
    }


    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        engine.process(jCas);
    }
}
