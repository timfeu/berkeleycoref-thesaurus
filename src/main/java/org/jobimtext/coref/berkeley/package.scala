package org.jobimtext.coref

/**
 * The contents of this package extend the Berkeley Coreference Resolution System with
 * support for distributional thesauri (DT).
 *
 * If you simply want to use existing holing systems, create an XML configuration file
 * following the documentation and schema (found in "config/thesaurus-config.xsd").
 * This way, you can enable features and set up the database connection.
 *
 * The system already comes with two holing systems implemented: [[org.jobimtext.coref.berkeley.thesaurus.StanfordDependencyThesaurus]] and [[org.jobimtext.coref.berkeley.thesaurus.TrigramThesaurus]].
 * You can specify the holing operation to use in the thesaurus configuration file
 * using the fully qualified class name.
 *
 * If you want to create your own holing operation, extend the trait
 * [[org.jobimtext.coref.berkeley.DistributionalThesaurusComputer]] and implement
 * the abstract members. See the trait's documentation for more information.
 *
 * If you want to add new features, modify the trait
 * [[org.jobimtext.coref.berkeley.DistributionalThesaurusComputer]] and add the
 * features to [[edu.berkeley.nlp.coref.PairwiseIndexingFeaturizerJoint]]. You also
 * have to extend the configuration schema. Features may have additional options; extend
 * the companion object of [[org.jobimtext.coref.berkeley.ThesaurusFeature]] to
 * conveniently reference options.
 *
 * Note that since the Berkeley System does not have a plugin mechanism for feature
 * computers, some of the changes required for the addition of DTs had to be made
 * directly to the system classes. In particular, have a look at
 * [[edu.berkeley.nlp.coref.PairwiseIndexingFeaturizerJoint]], which contains the
 * list of available features.
 */
package object berkeley {

}
