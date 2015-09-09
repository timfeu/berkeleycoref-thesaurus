package edu.berkeley.nlp.coref.lang

trait CorefLanguagePack {
  def getLanguage: Language

  def getMentionConstituentTypes: Seq[String];

  def getPronominalTags: Seq[String];

  def getProperTags: Seq[String];

  def isNoun(posTag: String): Boolean

  def isAdjective(posTag: String): Boolean

  def isVerb(posTag: String): Boolean
  
  def pronounsAgreeGender(pronounLemma1: String, pronounLemma2: String): Boolean

  def determiners: Set[String]

  def isPluralPos(pos: String): Boolean

  /**
   * The bare infinitive form of the most general verb that means "to say" in lowercase
   */
  def toSayVerb: String
}

class EnglishCorefLanguagePack extends CorefLanguagePack {
  override def getLanguage = Language.ENGLISH

  def getMentionConstituentTypes: Seq[String] = Seq("NP");

  def getPronominalTags: Seq[String] = Seq("PRP", "PRP$");

  def getProperTags: Seq[String] = Seq("NNP");

  override def isNoun(posTag: String): Boolean = posTag == "NN" || posTag == "NNS"

  override def isVerb(posTag: String): Boolean = posTag == "VB" || posTag == "VBD" || posTag == "VBG" || posTag ==
    "VBN" || posTag == "VBP" || posTag == "VBZ"

  override def isAdjective(posTag: String): Boolean = posTag == "JJ" || posTag == "JJR" || posTag == "JJS"

  override def isPluralPos(pos: String): Boolean = pos == "NNS" || pos == "NNPS"

  override def toSayVerb: String = "say"

  override def pronounsAgreeGender(pronounLemma1: String, pronounLemma2: String): Boolean = {
    def canonicalize(pronoun: String) = pronoun match {
      case "mine" | "my" | "me" | "myself" => "i"
      case "his" | "him" | "himself" => "he"
      case "her" | "herself" => "she"
      case "your" | "yourself" => "you"
      case "our" | "ourself" | "us" => "we"
      case "they" | "them" | "their" | "themselves" => "they"
      case "its" |"it" | "itself" => "it"
      case _ => pronoun
    }

    val p1 = canonicalize(pronounLemma1.toLowerCase)
    val p2 = canonicalize(pronounLemma2.toLowerCase)

    p1 match {
      case "he" => p2 == "he" || p2 == "i" || p2 == "you"
      case "she" => p2 == "she" || p2 == "i" || p2 == "you"
      case "it" => p2 == "it" || p2 == "they"
      case "they" => p2 == "it" || p2 == "they" || p2 == "we"
      case "we" => p2 == "they" || p2 == "we"
      case "i" => p2 != "it" && p2 != "they"
      case "you" => p2 != "it"
      case _ => true
    }
  }

  override def determiners: Set[String] = Set("the", "a", "an")
}

class ChineseCorefLanguagePack extends CorefLanguagePack {
  override def getLanguage = Language.CHINESE

  def getMentionConstituentTypes: Seq[String] = Seq("NP");

  def getPronominalTags: Seq[String] = Seq("PN");

  def getProperTags: Seq[String] = Seq("NR");

  override def isNoun(posTag: String): Boolean = ???

  override def isVerb(posTag: String): Boolean = ???

  override def isAdjective(posTag: String): Boolean = ???

  override def toSayVerb: String = ???

  override def pronounsAgreeGender(p1: String, p2: String): Boolean = ???

  override def determiners: Set[String] = ???

  override def isPluralPos(pos: String): Boolean = ???
}

class ArabicCorefLanguagePack extends CorefLanguagePack {
  override def getLanguage = Language.ARABIC

  def getMentionConstituentTypes: Seq[String] = Seq("NP");

  def getPronominalTags: Seq[String] = Seq("PRP", "PRP$");

  def getProperTags: Seq[String] = Seq("NNP");

  override def isNoun(posTag: String): Boolean = ???

  override def isVerb(posTag: String): Boolean = ???

  override def isAdjective(posTag: String): Boolean = ???

  override def toSayVerb: String = ???

  override def pronounsAgreeGender(p1: String, p2: String): Boolean = ???

  override def determiners: Set[String] = ???

  override def isPluralPos(pos: String): Boolean = ???
}