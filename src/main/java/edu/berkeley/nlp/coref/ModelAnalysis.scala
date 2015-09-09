package edu.berkeley.nlp.coref

import java.io.File

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.io.impl.ConllDocReader
import org.jobimtext.coref.berkeley.ThesaurusLoader

import scala.io.StdIn._

/**
 * Allows an interactive analysis of coreference decisions made by a model.
 *
 * @author Tim Feuerbach
 */
object ModelAnalysis {
  def interactive(config: CorefSystemConfiguration, modelPath: String) {
    val corefSystem = new CorefSystem(config)

    print("Loading model from " + modelPath)
    val modelContainer = CorefSystem.loadModelFile(modelPath)
    println(" [Done]")

    print("Creating property computer ")
    val computer = CorefSystem.createPropertyComputer(config)
    println(" [Done]")

    CorefSystem.ensureThesauriPresent(modelContainer, computer)

    modelContainer.scorer.featurizer.mentionPropertyComputer = computer

    val basicInferencer = new DocumentInferencerBasic(config)

    var docGraph: DocumentGraph = null
    var allPredBackptrs: Array[Int] = null
    var allPredClusterings: OrderedClustering = null

    println("Load a folder with gold documents by using the load <path> command")

    while (true) {
      val line = readLine("> ").trim
      println()

      val parts = line.split(" ")

      if (parts.size != 0) {

        val action = parts(0)

        action.trim.toLowerCase match {

          case "exit" | "quit" =>
            if (computer.thesauri != null) computer.thesauri.closeConnections()
            System.exit(0)

          case "load" => if (parts.size < 2) {
            println("No folder given!")
          } else {
            val path = parts(1)
            val docs = corefSystem.convertToCorefDocs(new ConllDocReader(config.languagePack.getLanguage,
              config.useNer).loadDocsFromFolder(new File(path), Some(ConllDocReader.suffixFilenameFilter
              ("auto_conll")), None), computer)

            var doc: CorefDoc = null

            if (docs.size == 0) {
              println("Empty folder, no docs loaded!")
            } else if (docs.size == 1) {
              doc = docs(0)
            } else {
              println("There are " + docs.size + " docs. Choose one.")
              val docId = readInt()
              doc = docs(docId)
            }

            println("Loaded document " + doc.rawDoc.docID + ", part " + doc.rawDoc.docPartNo)

            print("Featurizing one document");
            docGraph = new DocumentGraph(doc, false)
            new CorefFeaturizerTrainer(config).featurizeBasic(List(docGraph), modelContainer.scorer.featurizer)
            println(" [Done]")
            print("Inference")
            val tup = basicInferencer.viterbiDecodeAllFormClusterings(List(docGraph), modelContainer.scorer)
            allPredBackptrs = tup._1(0)
            allPredClusterings = tup._2(0)

            println(" [Done]")
            print("Clearing thesaurus cache ")
            if (computer.thesauri != null) {
              computer.thesauri.all.foreach(_.clearCache())
            }
            println(" [Done]")
          }

          case "mentions" | "m" => if (docGraph == null) {
            println("no document loaded")
          } else {
            docGraph.getMentions().zipWithIndex.foreach(println)
          }

          case "predecessor" | "p" => if (docGraph == null) {
            println("no document loaded")
          } else if (parts.size < 2) {
            println("predecessor of what mention?")
          } else {
            val mentIdx = parts(1).toInt
            if (mentIdx < 0 || mentIdx > docGraph.size() - 1) println(mentIdx + " does not exist")
            else {
              println("Backpointer of " + docGraph.getMention(mentIdx) + ":")
              println(docGraph.getMention(allPredBackptrs(mentIdx)) + "(" + allPredBackptrs(mentIdx) + ")")
            }
          }

          case "feats" | "f" => if (docGraph == null) {
            println("no document loaded")
          } else if (parts.size < 3) {
            println("require mention and antecedent")
          } else {
            val mentIdx = parts(1).toInt
            val antecedentIdx = parts(2).toInt
            if (antecedentIdx > mentIdx) println("antecedent idx can't be greater than ment idx")
            else {

              println("Feats of " + docGraph.getMention(mentIdx) + " -> " + docGraph.getMention(antecedentIdx) + ":")
              val featuresWithWeights = docGraph.cachedFeats(mentIdx)(antecedentIdx).map(featureIndex =>
                (modelContainer.scorer.featurizer.getIndexer().

                getObject(featureIndex), modelContainer.scorer.weights(featureIndex))).sortBy[Double](-_._2)
              featuresWithWeights.foreach(println)
              println("-------------------------------")
              println("Sum: " + featuresWithWeights.map(_._2).sum)
            }
          }

          case "sentence" | "s" => if (docGraph == null) {
            println("no document loaded")
          } else if (parts.size < 2) {
            println("sentence of what mention?")
          } else {
            val mentIdx = parts(1).toInt
            if (mentIdx < 0 || mentIdx > docGraph.size() - 1) println(mentIdx + " does not exist")
            else {
              val mention = docGraph.getMention(mentIdx)
              println(docGraph.corefDoc.rawDoc.words(mention.sentIdx).mkString(" "))
            }
          }

          case "head" | "h" => if (docGraph == null) {
            println("no document loaded")
          } else if (parts.size < 2) {
            println("predecessor of what mention?")
          } else {
            val mentIdx = parts(1).toInt
            val mention = docGraph.getMention(mentIdx)
            printf("H(%s) = %s", mention.toString, mention.headString)
            println()
          }

          case _ => println("Unknown command, try again (use exit to exit)")
        }
      }
    }

  }
}
