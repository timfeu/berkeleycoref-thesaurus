package org.jobimtext.coref.berkeley.bansalklein

import edu.berkeley.nlp.futile.util.Logger

import scala.collection.mutable

/**
 * To save memory when loading multiple Bansal & Klein features operating on simple pairs of features, this store is
 * used.
 */
object HeadPairFeatureStore {
  var store = new mutable.HashMap[(String, String), Array[String]]()

  type HeadsToFeature = ((String, String), String)

  val featureIndexCount = 5

  val CoOccurrenceFeatureIdx = 0
  val ClusterFeatureIdx = 1
  val HearstFeatureIdx = 2
  val EntitySimpleFeatureIdx = 3
  val EntityPosFeatureIdx = 4

  val featureLoaded = new Array[Boolean](5)

  /**
   * Retrieves a feature value from the feature store. Loads features if not present.
   *
   * @param featureIdx The index representing the feature, e.g. [[HeadPairFeatureStore.CoOccurrenceFeatureIdx]]
   * @param headPair the pair of heads to look up
   * @param featureLoader a function for loading features. Should yield a tuple of features to add to the store.
   *                      The first parameter is the feature index (so the same loader can load multiple
   *                      features from the same file), the second is the actual feature tuple.
   * @return
   */
  def getFeature(featureIdx: Int, headPair: (String, String), featureLoader: => TraversableOnce[(Int, HeadsToFeature)]): Option[String] = {
    if (!featureLoaded(featureIdx)) {
      loadFeatures(featureLoader)
    }

    store.get(headPair) match {
      case None => None
      case Some(array) => array(featureIdx) match {
        case null => None
        case featureValue => Some(featureValue)
      }
    }
  }

  def loadFeatures(featureLoader: => TraversableOnce[(Int, HeadsToFeature)]): Unit = {
    var headsLoaded = 0

    for (tup <- featureLoader) {
      val featureIdx = tup._1
      val featurePair = tup._2
      val headPair = (featurePair._1._1.intern(), featurePair._1._2.intern())
      val featureValue = featurePair._2.intern()

      featureLoaded(featureIdx) = true

      val featureArray = store.get(headPair) match {
        case None => val newArray = new Array[String](featureIndexCount); store.put(headPair, newArray); newArray
        case Some(array) => array
      }
      featureArray(featureIdx) = featureValue

      headsLoaded += 1

      if (headsLoaded % 1000000 == 0) Logger.logss(s"Loaded ${headsLoaded / 1000000}M headpairs")
    }
  }
}
