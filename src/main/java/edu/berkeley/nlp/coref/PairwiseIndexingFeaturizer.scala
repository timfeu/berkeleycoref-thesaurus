package edu.berkeley.nlp.coref
import edu.berkeley.nlp.futile.fig.basic.Indexer
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.Logger
import scala.collection.JavaConverters._

trait PairwiseIndexingFeaturizer {

  var mentionPropertyComputer: MentionPropertyComputer
  
  def getIndexer(): Indexer[String];

  def getPairwiseFeatsEnabled: String

  def getIndex(feature: String, addToFeaturizer: Boolean): Int;

  def featurizeIndex(docGraph: DocumentGraph, currMentIdx: Int, antecedentIdx: Int, addToFeaturizer: Boolean): Seq[Int];
  
  def printFeatureTemplateCounts() {
    val indexer = getIndexer();
    val templateCounts = new Counter[String]();
    for (i <- 0 until indexer.size) {
      val currFeatureName = indexer.getObject(i);
      val currFeatureTemplateStop = currFeatureName.indexOf("=");
      if (currFeatureTemplateStop == -1) {
        Logger.logss("No =: " + currFeatureName);
      } else {
        templateCounts.incrementCount(currFeatureName.substring(0, currFeatureTemplateStop), 1.0);
      }
    }
    templateCounts.keepTopNKeys(200);
    if (templateCounts.size > 200) {
      Logger.logss("Not going to print more than 200 templates");
    }
    templateCounts.keySet().asScala.toSeq.sorted.foreach(template => Logger.logss(template + ": " + templateCounts.getCount(template).toInt));
  }

  /**
   * Clones this featurizer and replaces its property computer by the provided instance.
   *
   * Since the thesaurus cache is not thread safe, the property computer has to be provided for each thread separately.
   *
   * @param computer
   * @return
   */
  def clone(computer: MentionPropertyComputer): PairwiseIndexingFeaturizer
}