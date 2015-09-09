package org.jobimtext.coref.berkeley.bansalklein

import java.io.{BufferedWriter, FileWriter}

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.{BaseDoc, CorefDocAssembler, CorefSystem}

object HeadwordPairWriter {
  /**
   * Prints a list of head word pairs. For every pair of predicted mentions m_i, m_k with k < i, prints a line
   * with the two heads and a line with both swapped.
   */
  def printHeadwords(config: CorefSystemConfiguration, docs: => Seq[BaseDoc]): Unit = {
    val assembler = CorefDocAssembler(config)
    val corefDocs = docs.map(doc => assembler.createCorefDoc(doc, CorefSystem.createPropertyComputer(config)))

    val out = new BufferedWriter(new FileWriter("headwords.txt", true))
    val known = scala.collection.mutable.Set[(String, String)]()

    for (doc <- corefDocs) {
      for (i <- 0 until doc.numPredMents) {
        for (j <- 0 until i) {
          val h1 = doc.predMentions(i).headString.trim
          val h2 = doc.predMentions(j).headString.trim
          if (!known.contains((h1, h2))) {
            out.write(h1)
            out.write(" ")
            out.write(h2)
            out.write("\n")

            // mirrored
            out.write(h2)
            out.write(" ")
            out.write(h1)
            out.write("\n")

            known += ((h1, h2))
            known += ((h2, h1))
          }
        }
      }
    }

    out.close()
  }

  /**
   * Prints the headword list necessary for the pronoun context feature from Bansal & Klein. Writes the following
   * elements:
   * pronounHead nonPronounHead r r' isPossessive(boolean)
   *
   * where r is the first word following the pronoun, and r' the second. If the word would be outside the sentence
   * or is punctuation, it will be replaced by a placeholder [[PronounContext.Placeholder]]
   */
  def printPronounHeadwords(config: CorefSystemConfiguration, docs: => Seq[BaseDoc]): Unit = {
    val assembler = CorefDocAssembler(config)
    val corefDocs = docs.map(doc => assembler.createCorefDoc(doc, CorefSystem.createPropertyComputer(config)))

    val out = new BufferedWriter(new FileWriter("headwords_pronoun.txt", true))

    for (doc <- corefDocs) {
      for (i <- 0 until doc.numPredMents) {
        for (j <- 0 until i) {
          val m1 = doc.predMentions(i)
          val m2 = doc.predMentions(j)
          val h1 = m1.headString.trim
          val h2 = m2.headString.trim
          if (PronounContext.isFeatureApplicable(m1, m2)) {
            out.write(h1)
            out.write(" ")
            out.write(h2)
            out.write(" ")
            out.write(PronounContext.getR1(m1))
            out.write(" ")
            out.write(PronounContext.getR2(m1))
            out.write(" ")
            out.write(PronounContext.isPossessive(m1).toString)
            out.write("\n")
          }
        }
      }
    }

    out.close()
  }
}
