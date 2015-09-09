package edu.berkeley.nlp.coref.lang

object LanguagePackFactory {
  def getLanguagePack(lang: Language) = lang match {
    case Language.ARABIC => new ArabicCorefLanguagePack
    case Language.CHINESE => new ChineseCorefLanguagePack
    case Language.ENGLISH => new EnglishCorefLanguagePack
  }
}
