package org.jobimtext.coref.berkeley

import edu.berkeley.nlp.coref.{CorefSystem, CorefDocAssembler}
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.io.impl.ConllDocReader
import edu.berkeley.nlp.coref.lang.Language

object StatisticsCollector {
  def debug(config: CorefSystemConfiguration, language: Language, useNer: Boolean): Unit = {
    val reader = new ConllDocReader(language, useNer)
    val docs = reader.loadDocs("in/wsj_0037.v2_auto_conll")
    val assembler = CorefDocAssembler(config)
    val computer = CorefSystem.createPropertyComputer(config)
    val corefDocs = docs.map(doc => assembler.createCorefDoc(doc, computer))

    val theDoc = corefDocs(0)
    val mentionA = theDoc.predMentions(347)
    val mentionB = theDoc.predMentions(352)

    val thesaurus = computer.thesauri.getByKey("stanford")

    System.out.println("Mention A: " + mentionA.spanToString)
    System.out.println("Mention B: " + mentionB.spanToString)

    System.out.println("Shared prior: " + thesaurus.sharedPriorExpansionCount(mentionA, mentionB))
    System.out.println("Prior A in B: " + thesaurus.positionInPriorExpansion(mentionA, mentionB))
    System.out.println("Prior B in A: " + thesaurus.positionInPriorExpansion(mentionB, mentionA))

    System.out.println("A IS-IS-A B: " + thesaurus.headIsIsa(mentionB, mentionA))
    System.out.println("B IS-IS-A A: " + thesaurus.headIsIsa(mentionA, mentionB))
    System.out.println("shared IS-As: " + thesaurus.headsSharedIsas(mentionB, mentionA).get._1)

    System.out.println("A context: " + thesaurus.getContext(mentionA))
    System.out.println("B context: " + thesaurus.getContext(mentionB))
    System.out.println("A C-expansion: " + thesaurus.contextExpansion(mentionA).take(50).map(_._1).mkString(","))
    System.out.println("B C-expansion: " + thesaurus.contextExpansion(mentionB).take(50).map(_._1).mkString(","))
    System.out.println("A in B C-expansion: " + thesaurus.positionInContextExpansion(mentionA, mentionB))
    System.out.println("B in A C-expansion: " + thesaurus.positionInContextExpansion(mentionB, mentionA))

  }
}
