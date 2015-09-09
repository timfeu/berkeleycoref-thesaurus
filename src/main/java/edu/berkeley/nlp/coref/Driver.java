package edu.berkeley.nlp.coref;

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration;
import edu.berkeley.nlp.coref.config.InferenceType;
import edu.berkeley.nlp.coref.config.PredictionCorefSystemConfiguration;
import edu.berkeley.nlp.coref.config.TrainingCorefSystemConfiguration;
import edu.berkeley.nlp.coref.io.impl.ConllDocReader;
import edu.berkeley.nlp.coref.lang.CorefLanguagePack;
import edu.berkeley.nlp.coref.lang.Language;
import edu.berkeley.nlp.coref.lang.LanguagePackFactory;
import edu.berkeley.nlp.futile.fig.basic.Option;
import edu.berkeley.nlp.futile.fig.exec.Execution;
import edu.berkeley.nlp.futile.util.Logger;
import org.jobimtext.coref.berkeley.DistributionalThesaurusComputer;
import org.jobimtext.coref.berkeley.Function0Helper;
import org.jobimtext.coref.berkeley.JavaHelper;
import org.jobimtext.coref.berkeley.ThesaurusLoader;
import org.jobimtext.coref.berkeley.bansalklein.*;
import scala.Function0;
import scala.collection.Seq;

import java.io.File;


public class Driver implements Runnable {

    @Option(gloss = "Which experiment to run?")
    public static Mode mode = Mode.TRAIN_EVALUATE;
    @Option(gloss = "Language choice")
    public static Language lang = Language.ENGLISH;

    // DATA AND PATHS
    @Option(gloss = "Path to CoNLL evaluation script")
    public static String conllEvalScriptPath = "lib/reference-coreference-scorers/scorer.pl";
    @Option(gloss = "Path to number/gender data")
    public static String numberGenderDataPath = "data/gender.data";
    @Option(gloss = "Use the POS tag to determine the number of common nouns")
    public static boolean usePOSForNumberCommon = false;
    @Option(gloss = "Path to the distributional thesaurus XML configuration file containing all thesauri database " +
            "connections to be used during the run")
    public static String dtConfPath = "conf/dt.xml";
    @Option(gloss = "Path to a list of stopwords to be ignored during context expansion. Used by holing systems that " +
            "have no access to POS tags")
    public static String stopwordListPath = "data/stopwords.txt";
    @Option(gloss = "Uses the distributional thesaurus features but provides only a dummy database interface that " +
            "returns nothing (or the input). Useful for checking whether the holing operation or features by " +
            "themselves " +
            "increase the performance")
    public static boolean dummyThesaurus = false;

    @Option(gloss = "Path to training set")
    public static String trainPath = "";
    @Option(gloss = "Training set size, -1 for all")
    public static int trainSize = -1;
    @Option(gloss = "Path to development set. If not empty, the system will train on both train and dev set.")
    public static String devPath = "";
    @Option(gloss = "Dev set size, -1 for all")
    public static int devSize = -1;
    @Option(gloss = "Path to test set")
    public static String testPath = "";
    @Option(gloss = "Test set size, -1 for all")
    public static int testSize = -1;
    @Option(gloss = "Suffix to use for documents")
    public static String docSuffix = "auto_conll";

    @Option(gloss = "Maximum number of randomized drawn training documents. Only used if value > 0")
    public static int randomTrainSize = 0;

    @Option(gloss = "Seed to use for randomized drawn training documents. The value here is the timestamp" +
            " when the entry was first created. Remember to use the same seed for comparisons.")
    public static long randomTrainSeed = 1417000694L;

    @Option(gloss = "Path to read/write the model")
    public static String modelPath = "";
    @Option(gloss = "Path to write prediction output to")
    public static String outputPath = "";
    @Option(gloss = "Directory to write output CoNLL files to when using the scorer. If blank, " +
            "uses the default temp directory and deletes them after. " +
            "You might want this because calling the scorer forks the process and may give an out-of-memory error, " +
            "so this is some insurance that at least you'll have your output.")
    public static String conllOutputDir = "";

    @Option(gloss = "Use NER in the system or ignore it?")
    public static boolean useNer = true;
    @Option(gloss = "True if we should train on the documents with gold annotations, " +
            "false if we should use auto annotations")
    public static boolean trainOnGold = false;
    @Option(gloss = "Use gold mentions.")
    public static boolean useGoldMentions = false;
    @Option(gloss = "Can toggle whether written output is filtered for singletons or not")
    public static boolean doConllPostprocessing = true;
    @Option(gloss = "Include appositive mentions?")
    public static boolean includeAppositives = true;

    @Option(gloss = "Print per-document scores for bootstrap significance testing")
    public static boolean printSigSuffStats = false;

    // PERFORMANCE OPTIONS
    @Option(gloss = "Whether distributed thesaurus information should be cached. The cache will be emptied after each" +
            " processed document.")
    public static boolean dtUseCache = true;

    // ORACLE OPTIONS
    @Option(gloss = "Use cheating clusters?")
    public static boolean cheat = false;
    @Option(gloss = "Number of cheating clusters")
    public static int numCheatingProperties = 3;
    @Option(gloss = "Domain size for each cheating cluster")
    public static int cheatingDomainSize = 5;

    // PHI FEATURE OPTIONS
    @Option(gloss = "Use phi-feature based clusters?")
    public static boolean phi = false;
    @Option(gloss = "Which phi cluster features to include (numb, gend, or nert)")
    public static String phiClusterFeatures = "";

    // TRAINING AND INFERENCE
    // These settings are reasonable and quite robust; you really shouldn't have
    // to tune the regularizer. I didn't find adjusting it useful even when going
    // from thousands to millions of features.
    @Option(gloss = "eta for Adagrad")
    public static double eta = 1.0;
    @Option(gloss = "Regularization constant (might be lambda or c depending on which algorithm is used)")
    public static double reg = 0.001;
    @Option(gloss = "Loss fcn to use")
    public static String lossFcn = "customLoss-0.1-3-1";
    @Option(gloss = "Loss fcn to use")
    public static String lossFcnSecondPass = "customLoss-0.1-3-1";
    @Option(gloss = "Number of iterations")
    public static int numItrs = 20;
    @Option(gloss = "Number of iterations")
    public static int numItrsSecondPass = 20;
    @Option(gloss = "Pruning strategy for coarse pass. No pruning by default")
    public static String pruningStrategy = "distance:10000:5000";
    @Option(gloss = "Pruning strategy for fine pass")
    public static String pruningStrategySecondPass = "c2flogratio:2";

    @Option(gloss = "Inference type")
    public static InferenceType inferenceType = InferenceType.PAIRWISE;
    @Option(gloss = "Features to use; default is SURFACE, write \"+FINAL\" for FINAL")
    public static String pairwiseFeats = "";
    @Option(gloss = "Features to use for the fine pass; default is SURFACE, write \"+FINAL\" for FINAL")
    public static String pairwiseFeatsSecondPass = "";
    @Option(gloss = "Conjunction type")
    public static ConjType conjType = ConjType.CANONICAL;
    @Option(gloss = "Conjunction type for the fine pass")
    public static ConjType conjTypeSecondPass = ConjType.CANONICAL;
    @Option(gloss = "Conjunction type for distributional thesaurus features")
    public static ConjType dtConjType = ConjType.CANONICAL;
    @Option(gloss = "Cutoff below which lexical features fire POS tags instead")
    public static int lexicalFeatCutoff = 20;
    @Option(gloss = "Factor for the maximum value to calculate the interval steps for discretization of integer " +
            "values. The default " +
            "value of 0.1, for example, would yield steps of 10 if the maximum value is 100. The resulting step will " +
            "be floored. A value of 0 or less results in no discretizing (the value itself will be taken), " +
            "which can be reasonable if the maximum value is not that big, though it requires a large training set to" +
            " yield useful weights. Percentage values will always be discretized.")
    public static double discretizeIntervalFactor = 0.1;

    @Option(gloss = "If set to true, all incompatible terms will be removed from a prior expansion")
    public static boolean dtRemoveIncompatibleTerms = false;
    @Option(gloss = "Remove incompatible terms if the database lists them at least k+1 times. Has no effect if " +
            "dtRemoveIncompatibleTerms is set to false.")
    public static int dtRemoveIncompatibleTermsK = 1;

    @Option(gloss = "Destination of the precomputed chimerge intervals to use")
    public static String chimergeIntervalsFile = "conf/chimerge.properties";

    @Option(gloss = "Decode with max or with left-to-right marginalization?")
    public static String decodeType = "basic";

    // Bansal & Klein web features
    @Option(gloss = "Path to file with pre-computed general co-occurrence features")
    public static String bkGeneralCoOccurrenceFeats = "";

    @Option(gloss = "Path to file with pre-computed hearst features")
    public static String bkHearstFeats = "";

    @Option(gloss = "Path to file with pre-computed entity features")
    public static String bkEntityFeats = "";

    @Option(gloss = "top k entries used for entity seed feature")
    public static int simpleMatchK = 10;

    @Option(gloss = "top k' entries used for dominant POS entity features")
    public static int posMatchK = 10;

    @Option(gloss = "Path to file with pre-computed pronoun cluster features WITHOUT BINNING")
    public static String bkClusterFeats = "";

    @Option(gloss = "Path to file with pre-computed pronoun context features WITHOUT BINNING")
    public static String bkPronounContextFeats = "";

    @Option(gloss = "The bin size for the Bansal&Klein R1 pronoun context feature")
    public static double bkR1PronounContextBinSize = 1.0;

    @Option(gloss = "The bin size for the Bansal&Klein R2 pronoun context feature")
    public static double bkR2PronounContextBinSize = 1.0;

    @Option(gloss = "The bin size for the Bansal&Klein R1Gap pronoun context feature")
    public static double bkR1GapPronounContextBinSize = 1.0;

    // other features related to B&K
    @Option(gloss = "Path to file with pre-computed head incompatibility features")
    public static String incompatibilityFeats = "";

    // BINARY OPTIONS
    @Option(gloss = "")
    public static double binaryLogThreshold = 0.0;
    @Option(gloss = "")
    public static boolean binaryNegateThreshold = false;
    @Option(gloss = "")
    public static String binaryClusterType = "TRANSITIVE_CLOSURE";
    @Option(gloss = "")
    public static double binaryNegativeClassWeight = 1.0;

    // RAHMAN/LOOPY OPTION
    @Option(gloss = "What kind of loopy featurization to use")
    public static String clusterFeats = "";

    // RAHMAN OPTIONS
    @Option(gloss = "")
    public static String rahmanTrainType = "goldusepred";

    // LOOPY OPTIONS
    @Option(gloss = "What to regularize the projected default weights towards")
    public static String projDefaultWeights = "agreeheavy";
    @Option(gloss = "Number of coref clusters to use (may be reduced from number of total clusters)")
    public static int corefClusters = 4;

    // ANALYSIS OPTIONS
    @Option(gloss = "Analyses to print: +purity, +discourse, +categorizer, +mistakes")
    public static String analysesToPrint = "";
    @Option(gloss = "Collect additional thesaurus statistics (consumes a lot of memory)")
    public static boolean dtStatistics = false;

    public static enum Mode {
        TRAIN, EVALUATE, PREDICT, TRAIN_EVALUATE, /*TRAIN_PREDICT,*/ TWO_PASS, OUTPUT_WEIGHTS,
        MODEL_ANALYSIS, COLLECT_STATISTICS, SPLIT_PREDICTIONS, PRINT_HEADWORD_PAIRS, PRINT_HEADWORD_PAIRS_PRONOUN_CONTEXT;
    }


    public static void main(String[] args) {
        Driver main = new Driver();
        Execution.run(args, main); // add .class here if that class should receive command-line args
    }

    public void run() {
        CorefLanguagePack languagePack = LanguagePackFactory.getLanguagePack(lang);
        NumberGenderComputer ngComputer = NumberGenderComputer.readBergsmaLinData(numberGenderDataPath);

        Logger.setFig();
        if (mode == Mode.TRAIN) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            new CorefSystem(config).runTrain(loadTrainingDocuments(), modelPath);
        } else if (mode == Mode.EVALUATE) {
            CorefSystemConfiguration config = new PredictionCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            new CorefSystem(config).runEvaluate(loadTestDocuments(), modelPath, conllEvalScriptPath);
        } else if (mode == Mode.PREDICT) {
            CorefSystemConfiguration config = new PredictionCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            new CorefSystem(config).runPredict(loadTestDocuments(), modelPath, outputPath, doConllPostprocessing);
        } else if (mode == Mode.TRAIN_EVALUATE) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            CorefSystem corefSystem = new CorefSystem(config);

            PairwiseScorer scorer = (modelPath == null || modelPath.isEmpty()) ? corefSystem.runTrain(loadTrainingDocuments()) : corefSystem.runTrain(loadTrainingDocuments(), modelPath);

            // reload thesaurus collection as it will be disconnected / nullified
            config.setThesaurusCollection(ThesaurusLoader.loadThesaurusCollection(new File(dtConfPath), config, dummyThesaurus));
            scorer.featurizer().mentionPropertyComputer_$eq(corefSystem.createPropertyComputer());

            corefSystem.runEvaluate(loadTestDocuments(), corefSystem.createModelContainer(scorer), conllEvalScriptPath);
        } /*else if (mode == Mode.TRAIN_PREDICT) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            new CorefSystem(config).runTrainPredict(loadTrainingDocuments(), loadTestDocuments(), modelPath, outputPath,
                    doConllPostprocessing);
        }*/ else if (mode == Mode.TWO_PASS) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            new CorefSystem(config).runNewOnlyTwoPass(loadTrainingDocuments(), loadTestDocuments(), conllEvalScriptPath);
        } else if (mode == Mode.OUTPUT_WEIGHTS) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            new CorefSystem(config).outputWeights(modelPath);
        } else if (mode == Mode.MODEL_ANALYSIS) {
            CorefSystemConfiguration config = new PredictionCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            Logger.endTrack();

            ModelAnalysis.interactive(config, modelPath);
        } else if (mode == Mode.PRINT_HEADWORD_PAIRS) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            Logger.endTrack();
            HeadwordPairWriter.printHeadwords(config, loadTrainingDocuments());
        } else if (mode == Mode.PRINT_HEADWORD_PAIRS_PRONOUN_CONTEXT) {
            CorefSystemConfiguration config = new TrainingCorefSystemConfiguration(languagePack, ngComputer);
            populateConfiguration(config);

            Logger.endTrack();
            HeadwordPairWriter.printPronounHeadwords(config, loadTrainingDocuments());
        }
    }

    /**
     * Overrides all settings in the configuration with driver argument values and loads the thesauri.
     *
     * @param config the configuration
     */
    protected void populateConfiguration(CorefSystemConfiguration config) {
        config.setUsePOSForNumberCommon(usePOSForNumberCommon);
        config.setUseNer(useNer);
        config.setTrainOnGold(trainOnGold);
        config.setUseGoldMentions(useGoldMentions);
        config.setRemoveSingletons(doConllPostprocessing);
        config.setIncludeAppositives(includeAppositives);
        config.setPrintSigSuffStats(printSigSuffStats);
        config.setDtUseCache(dtUseCache);
        config.setCheat(cheat);
        config.setNumCheatingProperties(numCheatingProperties);
        config.setCheatingDomainSize(cheatingDomainSize);
        config.setPhi(phi);
        config.setPhiClusterFeatures(phiClusterFeatures);
        config.setEta(eta);
        config.setReg(reg);
        config.setLossFcn(lossFcn);
        config.setLossFcnSecondPass(lossFcnSecondPass);
        config.setNumItrs(numItrs);
        config.setNumItrsSecondPass(numItrsSecondPass);
        config.setPruningStrategy(pruningStrategy);
        config.setPruningStrategySecondPass(pruningStrategySecondPass);
        config.setInferenceType(inferenceType);
        config.setPairwiseFeats(pairwiseFeats);
        config.setPairwiseFeatsSecondPass(pairwiseFeatsSecondPass);
        config.setConjType(conjType);
        config.setConjTypeSecondPass(conjTypeSecondPass);
        config.setDtConjType(dtConjType);
        config.setLexicalFeatCutoff(lexicalFeatCutoff);
        config.setDiscretizeIntervalFactor(discretizeIntervalFactor);
        config.setDtRemoveIncompatibleTerms(dtRemoveIncompatibleTerms);
        config.setDtRemoveIncompatibleTermsK(dtRemoveIncompatibleTermsK);
        config.setChimergeIntervalsFile(chimergeIntervalsFile);
        config.setDecodeType(decodeType);
        config.setBinaryLogThreshold(binaryLogThreshold);
        config.setBinaryClusterType(binaryClusterType);
        config.setBinaryNegativeClassWeight(binaryNegativeClassWeight);
        config.setBinaryNegateThreshold(binaryNegateThreshold);
        config.setClusterFeats(clusterFeats);
        config.setRahmanTrainType(rahmanTrainType);
        config.setProjDefaultWeights(projDefaultWeights);
        config.setCorefClusters(corefClusters);
        config.setAnalysesToPrint(analysesToPrint);
        config.setDtStatistics(dtStatistics);
        config.setConllOutputDir(conllOutputDir);

        config.setAdditionalProperty(DistributionalThesaurusComputer.class, "stopwordListPath", stopwordListPath);

        // Bansal & Klein features
        config.setAdditionalProperty(GeneralCoOccurrence$.class, GeneralCoOccurrence.FeatureFileOption(), bkGeneralCoOccurrenceFeats);
        config.setAdditionalProperty(HearstPattern$.class, HearstPattern.FeatureFileOption(), bkHearstFeats);
        config.setAdditionalProperty(Entity$.class, Entity.FeatureFileOption(), bkEntityFeats);
        config.setAdditionalProperty(Entity$.class, Entity.simpleMatchKOption(), simpleMatchK);
        config.setAdditionalProperty(Entity$.class, Entity.posMatchKOption(), posMatchK);
        config.setAdditionalProperty(Cluster$.class, Cluster.FeatureFileOption(), bkClusterFeats);
        config.setAdditionalProperty(PronounContext$.class, PronounContext.FeatureFileOption(), bkPronounContextFeats);
        config.setAdditionalProperty(PronounContext$.class, PronounContext.R1BinSizeOption(), bkR1PronounContextBinSize);
        config.setAdditionalProperty(PronounContext$.class, PronounContext.R2BinSizeOption(), bkR2PronounContextBinSize);
        config.setAdditionalProperty(PronounContext$.class, PronounContext.R1GapBinSizeOption(), bkR1GapPronounContextBinSize);

        // not B&K, but similar features
        config.setAdditionalProperty(Incompatibility$.class, Incompatibility.FeatureFileOption(), incompatibilityFeats);

        config.setThesaurusCollection(ThesaurusLoader.loadThesaurusCollection(new File(dtConfPath), config, dummyThesaurus));
    }

    protected Function0<scala.collection.Seq<BaseDoc>> loadDocuments(final String path, final int size) {
        return new Function0Helper<Seq<BaseDoc>>() {

            @Override
            public scala.collection.Seq<BaseDoc> applyImpl() {
                ConllDocReader reader = new ConllDocReader(lang, useNer);

                return reader.loadDocsFromFolderSimple(new File(path), ConllDocReader.suffixFilenameFilter(docSuffix), size);
            }
        };
    }

    protected Function0<scala.collection.Seq<BaseDoc>> loadTrainingDocuments() {
        if (trainPath.equals(testPath) || devPath.equals(testPath) && !devPath.isEmpty()) {
            Logger.warn("Evaluating on (portion) of training set");
        }

        return new Function0Helper<scala.collection.Seq<BaseDoc>>() {

            @Override
            public Seq<BaseDoc> applyImpl() {
                scala.collection.Seq<BaseDoc> baseDocs = (devPath.isEmpty()) ? loadDocuments(trainPath, trainSize).apply() : JavaHelper.<BaseDoc>combineSequences(loadDocuments(trainPath, trainSize).apply(), loadDocuments(devPath, devSize).apply());

                if (randomTrainSize > 0) {
                    Logger.logss("Drawing " + randomTrainSize + " documents as training set");
                    return JavaHelper.drawXRandomElements(baseDocs, randomTrainSize, randomTrainSeed);
                } else {
                    return baseDocs;
                }
            }
        };
    }

    protected Function0<scala.collection.Seq<BaseDoc>> loadTestDocuments() {
        return loadDocuments(testPath, testSize);
    }
}
