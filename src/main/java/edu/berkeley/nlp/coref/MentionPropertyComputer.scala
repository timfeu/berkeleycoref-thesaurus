package edu.berkeley.nlp.coref

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import org.jobimtext.coref.berkeley.ThesaurusCollection
import edu.berkeley.nlp.coref.lang.CorefLanguagePack

/**
 * TODO convert nulls to options
 * @param languagePack
 */
class MentionPropertyComputer(val languagePack: CorefLanguagePack) {

  var ngComputer: NumberGenderComputer = null
  var thesauri: ThesaurusCollection = null
  var config: CorefSystemConfiguration = null
}