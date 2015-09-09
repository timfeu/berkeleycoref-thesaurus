package edu.berkeley.nlp.coref.preprocess

import edu.berkeley.nlp.futile.util.Logger
import edu.stanford.nlp.trees._
import scala.collection.mutable.ArrayBuffer
import edu.berkeley.nlp.futile.syntax.Trees.PennTreeRenderer
import java.io.{BufferedReader, StringReader, Reader}
import scala.collection.mutable

import scala.collection.JavaConversions._

case class Dependency(govIndex: Int, depIndex: Int, label: String, isGovernor: Boolean)

object PennTrebankToStanfordDependenciesConverter {
  lazy val gsf = {
    val tlp = new PennTreebankLanguagePack()
    tlp.grammaticalStructureFactory()
  }

  /**
   * Converts the given sentences in penn treebank parse style to collapsed stanford dependencies.
   *
   * @param pennParse the sentences in pen treebank format
   *
   * @return an array containing sentences, with each sentences dependencies
   */
  def computeDependencies(pennParse: Seq[edu.berkeley.nlp.futile.syntax.Tree[String]]) = {
    val resultBuffer = new ArrayBuffer[Array[TypedDependency]]

    val treebank = new MemoryTreebank(new TreeReaderFactory {
      override def newTreeReader(in: Reader): TreeReader = new PennTreeReader(in, new LabeledScoredTreeFactory())
    })

    for (sentence <- pennParse) {
      if (sentence.getChild(0).getLabel == "NOPARSE") {
        Logger.warn("no parse, ignoring sentence " + sentence.getTerminals.toSeq.map(_.getLabel).mkString(" "))
        resultBuffer += new Array[TypedDependency](0)
      } else {
        treebank.load(new BufferedReader(new StringReader(PennTreeRenderer.render(sentence))))



        // get inserted tree's dependencies
        try {
          val gs = gsf.newGrammaticalStructure(treebank.get(treebank.size - 1))
          resultBuffer += gs.typedDependenciesCollapsed().toArray(new Array[TypedDependency](0))
        } catch {
          case e: Exception => println(sentence.toString); throw e
        }
      }
    }

    resultBuffer.toArray
  }

  /**
   * Creates a map that enumerates for each word in each sentence all dependencies the word takes part.
   * Skips root dependency relations.
   *
   * @param dependencySentences the sentences with the typed dependencies for each word index
   */
  def createWordToDependencyMap(dependencySentences: Array[Array[TypedDependency]]) = {
    val sentences = mutable.LinkedHashMap.empty[Int, mutable.LinkedHashMap[Int, mutable.Set[Dependency]]]

    for (sentenceIndex <- 0 until dependencySentences.length) {
      val wordIdxToParticipatingDependencies = mutable.LinkedHashMap.empty[Int, scala.collection.mutable.Set[Dependency]]

      for (dependency <-  dependencySentences(sentenceIndex)) {
        val depIndex = dependency.dep.index() - 1
        val govIndex = dependency.gov.index() - 1
        if (depIndex < 0 || govIndex < 0) {
          // skip root
        } else {
          val relName = dependency.reln().toString.toLowerCase()

          wordIdxToParticipatingDependencies.getOrElseUpdate(depIndex, scala.collection.mutable.Set.empty[Dependency]) +=
          Dependency(govIndex, depIndex, relName, false)
          wordIdxToParticipatingDependencies.getOrElseUpdate(govIndex, scala.collection.mutable.Set.empty[Dependency]) +=
            Dependency(govIndex, depIndex, relName, true)
        }
      }

      sentences += sentenceIndex -> wordIdxToParticipatingDependencies
    }

    sentences
  }
}
