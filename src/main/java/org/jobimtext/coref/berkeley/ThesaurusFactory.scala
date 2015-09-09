package org.jobimtext.coref.berkeley

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import org.jobimtext.api.db.AntonymDatabase
import org.jobimtext.api.struct.IThesaurusDatastructure
import org.jobimtext.coref.berkeley.thesaurus.{StanfordDependencyThesaurus, TrigramThesaurus}
import edu.berkeley.nlp.coref.lang.{CorefLanguagePack, LanguagePackFactory}

/**
 * Creates thesauri based on the holing system specified by a class name.
 *
 * @author Tim Feuerbach
 */
object ThesaurusFactory {
  def createThesaurus(holingSystem: String, identifier: String, cache: ThesaurusCache,
                      interface: IThesaurusDatastructure[String, String], antonymDatabase: Option[AntonymDatabase],
                      maxPriorExpansions: Option[Int], config: CorefSystemConfiguration) = {
    val holingClass = Class.forName(holingSystem).asInstanceOf[Class[DistributionalThesaurusComputer]]

    val thesaurus = holingClass.getConstructor(classOf[String], classOf[ThesaurusCache],
      classOf[IThesaurusDatastructure[String, String]],
      classOf[CorefSystemConfiguration]).newInstance(identifier, cache, interface, config)

    thesaurus.antonymDatabase = antonymDatabase
    if (maxPriorExpansions.isDefined) thesaurus.maxPriorExpansionSize = maxPriorExpansions.get

    thesaurus
  }
}
