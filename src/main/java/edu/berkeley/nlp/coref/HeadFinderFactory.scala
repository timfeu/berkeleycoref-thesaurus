package edu.berkeley.nlp.coref

import edu.berkeley.nlp.coref.lang.{ModArabicHeadFinder, ModCollinsHeadFinder, Language}
import edu.berkeley.nlp.futile.ling.{BikelChineseHeadFinder, AbstractCollinsHeadFinder}

/**
 * Creates a suiting head finder for the given language.
 */
object HeadFinderFactory {
  def create(language: Language): AbstractCollinsHeadFinder = language match {
    case Language.ENGLISH => new ModCollinsHeadFinder();
    case Language.CHINESE => new BikelChineseHeadFinder();
    case Language.ARABIC => new ModArabicHeadFinder();
    case _ => throw new RuntimeException("Bad language, no head finder for " + language);
  }
}
