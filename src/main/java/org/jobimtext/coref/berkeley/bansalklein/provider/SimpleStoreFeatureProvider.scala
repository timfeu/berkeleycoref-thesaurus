package org.jobimtext.coref.berkeley.bansalklein.provider

import java.io.{FileInputStream, BufferedInputStream}
import java.util.zip.GZIPInputStream

import edu.berkeley.nlp.futile.util.Logger
import org.jobimtext.coref.berkeley.bansalklein.HeadPairFeatureStore
import org.jobimtext.coref.berkeley.bansalklein.HeadPairFeatureStore._

import scala.io.Source

class SimpleStoreFeatureProvider(path: String, featureIdx: Int) extends FeatureProvider {
  def loadFeatures(): TraversableOnce[(Int, HeadsToFeature)] = {
    if (path.isEmpty) throw new IllegalStateException("Feature used without using precomputed " +
      "features")

    val source = path.endsWith(".gz") match {
      case false => Source.fromFile(path)
      case true => Source.fromInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(path))))
    }

    Logger.logss(s"[B&K] Loading features from $path into memory...")
    new Iterator[(Int, HeadsToFeature)] {
      var closed = false
      val it = source.getLines().map(_.split(' ')).map(parts => {
        assert(parts.length == 3)
        (featureIdx, (parts(0), parts(1)) -> parts(2))
      })

      override def hasNext: Boolean = {
        val ret = it.hasNext
        if (!ret && !closed) {
          source.close()
          closed = true
          Logger.logss("[B&K] done loading features")
        }
        ret
      }

      override def next(): (Int, ((String, String), String)) = it.next()
    }
  }

  override def getFeature(h1: String, h2: String): Option[String] = {
    HeadPairFeatureStore.getFeature(featureIdx, (h1, h2), loadFeatures())
  }
}
