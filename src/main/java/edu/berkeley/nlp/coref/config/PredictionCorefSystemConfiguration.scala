package edu.berkeley.nlp.coref.config

import edu.berkeley.nlp.coref.NumberGenderComputer
import edu.berkeley.nlp.coref.lang.CorefLanguagePack

import scala.beans.BeanProperty

/**
 * A coref system configuration suitable for predicitons.
 *
 * @param languagePack A language pack providing language-specific information about words and POS tags
 * @param numberGenderComputer number/gender computer to use by the coref system
 *
 * @author Tim Feuerbach
 */
class PredictionCorefSystemConfiguration(@BeanProperty var languagePack: CorefLanguagePack,
                                         @BeanProperty var numberGenderComputer: NumberGenderComputer) extends DefaultCorefSystemConfiguration {

}
