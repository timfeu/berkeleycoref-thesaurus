Download precompiled system with all dependencies: https://github.com/timfeu/berkeleycoref-thesaurus/releases/download/v1.0/berkeleycoref-1.0d.zip

Bansal & Klein feature extractors: https://github.com/timfeu/berkeleycoref-bansalklein

--------
Preamble
--------

This software and its README is a modified version of the Berkeley Coreference
Resolution System, extending it with support for distributional thesauri created
by the JoBimText framework (http://www.jobimtext.org).

NOTE: The modified system no longer supports languages other than English.

The distributional features are described in:

"Distributional Semantics for Resolving Bridging Mentions"
Tim Feuerbach, Martin Riedl, and Chris Biemann. RANLP 2015.

The Berkeley Coreference Resolution System is a state-of-the-art English coreference
resolution system described in:

"Easy Victories and Uphill Battles in Coreference Resolution"
Greg Durrett and Dan Klein. EMNLP 2013.

This release also contains support for entity-level training and inference using
distributions over mention properties, as described in:

"Decentralized Entity-Level Modeling for Coreference Resolution"
Greg Durrett, David Hall, and Dan Klein. ACL 2013. 

See
http://www.eecs.berkeley.edu/~gdurrett/ for papers and BibTeX for the latter two.

You may download the unmodified Berkeley system from

http://nlp.cs.berkeley.edu/projects/coref.shtml

-------
License
-------

Modifications (c) 2014 Tim Feuerbach.

Copyright (c) 2013 Greg Durrett.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

--

This work includes sofware by the JoBimText project, licensed under
the GPLv3 compatible Apache License, Version 2.0

Copyright (c) 2012 IBM Corp. and FG Language Technology, Technische Universität Darmstadt

----------
Update Log
----------

2015-??-??: Initial release of modified version.

2013-11-11: Version 1.0
--Demonstrative pronouns are handled slightly differently in the featurization (helps slightly).
--Fixed a bug in the preprocessing where the last paragraph of raw text input would be dropped.
--Added better error handling and documentation for some common I/O problems.
--Now builds with the latest version of SBT

2013-11-03
--Now under the GPLv3

2013-10-29
--Now compiles under Scala 2.10.3 (was previously using the deprecated .first to get the first elements of Seqs)
--Bug fixes for running on raw text
--Included new pre-trained models that should perform better on raw text

2013-10-18
--Added pipeline support for running over raw text.

2013-09-19
--Preliminary version.

-----
Setup
-----

Coreference training and testing data:
Training and testing data are provided to the system as directories containing files ending in auto_conll or gold_conll
(usually the former, all experiments are done over auto annotation). These can be produced from the CoNLL shared task
data by flattening the directories as follows:

find unflattened -type f | awk '{ str=$0; sub(/\.\//, "", str);
gsub(/\//, "-", str); print "cp " $0 " flattened/" str}' | bash

(From http://stackoverflow.com/a/3228414/1478302; necessary since some files from different directories share the same name)

Number and gender data: Available from:
http://old-site.clsp.jhu.edu/~sbergsma/Gender/index.html
Unzip to produce gender.data. The system expects this at data/gender.data, so if you put it here you won't have to think about it.
(Unfortunately, the system always tries to load it even if it won't be used...) You can gzip the data.

CoNLL scorer: Available from:
http://conll.github.io/reference-coreference-scorers/
There will be three things in the download: scorer.pl, CorScorer.pm, and a directory called Algorithm.
Put Algorithm and CorScorer.pl into the lib/ folder. This way they'll be located for scoring. Provide the path to scorer.pl using the -conllEvalScriptPath command line option.

Note that all results in the paper come from version 7 of the CoNLL scorer. Other versions of the scorer may
return different results. v8.01 or v8 should produce the same values, since they only added the BLANC metric which we didn't use.

JobimText Thesaurus: Download model from:
http://sourceforge.net/projects/jobimtext/files/data/models/en_news120M_stanford_lemma/
You can use any JDBC-compliant database to store the data. Instructions for MySQL can be found on http://maggie.lt.informatik.tu-darmstadt.de/jobimtext/documentation/calculate-a-distributional-thesaurus-dt/ section
"Database Access".

Change username and password in the DT configuration files found in the "conf" directory to the appropriate values. Add the JDBC driver matching your
database into the target/pack/lib folder. For MySQL, you can obtain it from:
http://search.maven.org/remotecontent?filepath=mysql/mysql-connector-java/5.1.36/mysql-connector-java-5.1.34.jar

------------------------------
Running the coreference system
------------------------------

--> See HOWTO_BUILD.txt on instructions on how to build the system.

You can run the system from the script files in the bin directory. JVM options (like "-Xmx20g" to reserve the necessary memory
for training the system) should be providable by exporting the JVM_OPT variable before calling
the script. In case this doesn't work, modify the script files directly (last lines running
exec in "berkeleycoref" for Unix systems and line 92 in "berkeleycoref.bat" for Windows).

The main class is edu.berkeley.nlp.coref.Driver. You can run the system on Linux with:

sh target/pack/bin/berkeleycoref ++base.conf -execDir working -trainPath PATH_TO_TRAINING_DATA -testPath PATH_TO_DEV_OR_TEST_SET
                      -conllEvalScriptPath lib/reference-coreference-scorers/scorer.pl -numberGenderData data/gender.data					  
					  -mode TRAIN_EVALUATE -pairwiseFeats x+FINAL -discretizeIntervalFactor 0.1 -dtStatistics false -dtConjType CANONICAL -dtConfPath conf/pica.xml

					  
(Use the batch file on Windows instead)

This example will reproduce the PICA results from the paper with slight variance depending on the order in which
the training files are read by the operating system.

System modes:
TRAIN: Trains a model and writes it to modelPath.
  Requires: trainPath, modelPath
EVALUATE: On data with gold annotations, reads in a trained model and evaluates performance on that data using the CoNLL scorer.
  Requires: modelPath, testPath
PREDICT: On CoNLL-formatted data with no coreference information, reads in a trained model and writes to outputPath the same CoNLL data with predicted
  coreference clusters.
  Requires: modelPath, testPath, outputPath
TRAIN_EVALUATE, TRAIN_PREDICT: Trains a model and uses it for either EVALUATE or PREDICT as above. Model is not saved.
  Requires: trainPath, testPath
TWO_PASS: Runs TRAIN_EVALUATE, then prunes both training and testing data based on the trained model and retrains a new model
  with a new set of features and implements entity-level features as described in the ACL 2013.
  Requires: trainPath, testPath
PRINT_HEADWORD_PAIRS, PRINT_HEADWORD_PAIRS_PRONOUN_CONTEXT: Writes a list of headword pairs of mentions from the training documents. Used for Bansal & Klein features (see below)
OUTPUT_WEIGHTS: (only for debugging) Outputs features of a model together with their weights to standard output. 
MODEL_ANALYSIS: (only for debugging, undocumented) Interactive prompt that allows to investigate coreference decisions for single mentions. See class ModelAnalysis for commands.

Other options are discussed in edu.berkeley.nlp.coref.Driver. A few other useful ones:
-useGoldMentions
-trainOnGold (uses the gold_conll annotations)
-devPath if used in addition to trainPath, will merge documents from both paths. Can be used to train on conjunction of train+dev without the need to put them into one directory
-docSuffix (By default the system runs on auto CoNLL annotations. Change to gold_conll to run on gold annotations.)
-doConllPostprocessing (if false, allows you to keep singletons in output)
-numItrs (currently set to 20, can be set lower with only a small loss in performance to make the system train faster)
-printSigSuffStats (if true, prints results for each document so you can do bootstrap resampling siginificance tests)
-discretizeIntervalFactor: This adjusts the bin size for DT lookup features

Depending on how many documents you run on, the system may require 10-30GB of RAM to train (due to caching
feature vectors for every coreference arc). Prediction should be less intensive. On CoNLL-2011 data, we
observed a maximum usage of 24 GiB during the Bansal & Klein experiments.

Features: These can be tweaked using -pairwiseFeats. Look in PairwiseIndexingFeaturizerJoint.scala for specific
feature options. Note that all of the "magic words" start with +, but the whole string cannot start with + or the
system will crash due to the vagaries of the option parsing code. Using -pairwiseFeats "FINAL" will use the FINAL features.

Distributional thesaurus features: For clearness, these are configured separately in XML files found in the conf folder.
If you want to exclude the distributional features, use a non-existing file as -dtConfPath.

-------------------------
Bansal and Klein features
-------------------------

This component only runs on precomputed features and is therefore unsuitable for live applications.

First, you have to obtain a copy of the Google Web 1T 5-gram corpus (or any corpus with a similar format).
It is available at https://catalog.ldc.upenn.edu/LDC2006T13

You can download the phrasal clusters computed by Lin et al. from http://www.clsp.jhu.edu/~sbergsma/PhrasalClusters/

Next, generate a list of all pairs of mention heads you'll encounter:

sh target/pack/bin/berkeleycoref ++base.conf -execDir working -trainPath path/to/training/data PRINT_HEADWORD_PAIRS
sh target/pack/bin/berkeleycoref ++base.conf -execDir working -trainPath path/to/development/data PRINT_HEADWORD_PAIRS
sh target/pack/bin/berkeleycoref ++base.conf -execDir working -trainPath path/to/test/data PRINT_HEADWORD_PAIRS

This will produce a file "headwords.txt".

If you also want to use the pronoun features, repeat this step using mode PRINT_HEADWORD_PAIRS_PRONOUN_CONTEXT

To precompute the features, use the "BKComputer" tool provided separately. E.g. for general co-occurrence:

java generalCoOccurrence -jar BKComputer.jar -filePath_corefPairs headwords.txt -basePath_googlengrams path/to/ngrams -filePathWrite generalCoOccurrence.txt

The features can be used by providing the appropriate options to the coreference system:

... -bkGeneralCoOccurrenceFeats generalCoOccurrence.txt -pairwiseFeats x+FINAL+bkCoOccurrence

See classes Driver and BansalKleinFeaturizer for more information.

-------------
Preprocessing
-------------

The raw text processing that came with the original system has been replaced with an Apache UIMA annotation engine.
See HOWTO_UIMA.txt for instructions on how to integrate the system into a pipeline to run it on preprocessed text.

-------
  API
-------

Featurization is done in PairwiseIndexingFeaturizerJoint.featurizeIndex. This
adds the standard features (computed in featurizeIndexStandard). This is called
once for every mention pair, so it should be as optimized as possible. In
particular, properties specific to mentions should be cached in the Mention
objects if at all possible.

The easiest way to do this is to add accessors to Mention that implement a
cache (see Mention.cachedCanonicalPronConjStr for an example of this usage).
If you need properties that are computed with external tools, try adding them
to MentionPropertyComputer and then modfiying
Mention.createMentionComputeProperties.

Distributional Thesaurus features are implemented in org.jobimtext.coref.berkeley.DistributionalThesaurusComputer.
You can implement that trait to add new holing operations. Two are shipped with this distribution:
StanfordDependencyThesaurus as used in the paper, and TrigramThesaurus, which operates on simple trigrams with the hole in the middle.

------------
Known issues
------------

--If pairwiseFeats starts with +, the system will break due to how the options parsing works.
  --To resolve: add some character or word at the beginning; this won't change what features fire.
  
  Note: We did this in the above example call, hence the "x".
  
--Calling the scorer may cause an out-of-memory error because under the hood, Java
  forks the process and if you're running with a lot of memory, it will crash.
  --To resolve: run in PREDICT or TRAIN_PREDICT mode and manually call the scorer separately.

---------------
Troubleshooting
---------------

Common errors:

*  0 docs loaded from 0 files, retaining 0
  ERROR: java.lang.UnsupportedOperationException: empty.reduceLeft:
  [...scala.collection calls...]
  edu.berkeley.nlp.coref.CorefDoc$.checkGoldMentionRecall(CorefDoc.scala:99)
  edu.berkeley.nlp.coref.CorefSystem$.loadCorefDocs(CorefSystem.scala:56)
  edu.berkeley.nlp.coref.CorefSystem$.runEvaluate(CorefSystem.scala:162)
  edu.berkeley.nlp.coref.CorefSystem$.runEvaluate(CorefSystem.scala:157)
  edu.berkeley.nlp.coref.CorefSystem.runEvaluate(CorefSystem.scala)
  edu.berkeley.nlp.coref.Driver.run(Driver.java:164)
  edu.berkeley.nlp.futile.fig.exec.Execution.runWithObjArray(Execution.java:479)
  edu.berkeley.nlp.futile.fig.exec.Execution.run(Execution.java:432)
  edu.berkeley.nlp.coref.Driver.main(Driver.java:156)

--> This means the system didn't load any documents. It expects files to end in auto_conll in
the given directories. If you want to run on gold_conll documents, use the -docSuffix flag
to change this.

* Out of memory errors:

--> Ensure that the Java process has around 23GiB of memory available for training. For using a precomputed model, you need at least
as much memory as the uncompressed model, since it will be loaded completely into RAM. To configure the amount of memory provided,
use the  JVM option "-Xmx", e.g. "-Xmx21g" (note that there is no space between option name and value). Export the JVM_OPT variable before calling
the script to set this option. In case this doesn't work, modify the script files directly (last lines running
exec in "berkeleycoref" for Unix systems and line 92 in "berkeleycoref.bat" for Windows).
