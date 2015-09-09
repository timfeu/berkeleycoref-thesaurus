package org.jobimtext.coref.berkeley.bansalklein

import java.io.{FileInputStream, BufferedInputStream}
import java.util.zip.GZIPInputStream

import edu.berkeley.nlp.coref.Mention
import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.futile.util.Logger
import org.jobimtext.api.db.DatabaseConnection
import org.jobimtext.coref.berkeley.bansalklein.HeadPairFeatureStore._
import org.jobimtext.coref.berkeley.bansalklein.provider.FeatureProvider

import scala.collection.mutable
import scala.io.Source

object Entity {
  val FeatureFileOption = "featureFile"
  val simpleMatchKOption = "simpleMatchK"
  val posMatchKOption = "posMatchK"

  private abstract class StoreFeatureProvider extends FeatureProvider {
    protected def loadFeatures(configuration: CorefSystemConfiguration): Iterator[(Int, HeadsToFeature)] = {
      val path = configuration.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
      if (path.isEmpty) throw new IllegalStateException("Entity feature used without using precomputed " +
        "features")

      val simpleK = configuration.getAdditionalProperty(getClass, simpleMatchKOption, -1).asInstanceOf[Int]
      val posK = configuration.getAdditionalProperty(getClass, posMatchKOption, -1).asInstanceOf[Int]

      val source = path.endsWith(".gz") match {
        case false => Source.fromFile(path)
        case true => Source.fromInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(path))))
      }

      Logger.logss(s"[B&K] Loading entity features from $path into memory...")
      if (simpleK <= 0) Logger.logss("ignoring simple seed matches since none or negative k specified")
      if (posK <= 0) Logger.logss("ignoring dominant POS of matches since none or negative k' specified")

      val it = source.getLines()

      val simpleKs = it.next().split(" ")
      val posKs = it.next().split(" ")

      val simpleIdx = if (simpleK > 0) {
        val idx = simpleKs.indexOf(simpleK.toString)
        assert(idx > -1, "k=" + simpleK + " for simple seed match was not found in precomputed feature list")
        Some(2 + idx)
      } else None

      val posIdx = if (posK > 0) {
        val idx = posKs.indexOf(posK.toString)
        assert(idx > -1, "k=" + posK + " for dominant POS seed match was not found in precomputed feature list")
        Some(2 + simpleKs.length + idx)
      } else None

      new Iterator[(Int, HeadsToFeature)] {
        var closed = false

        val _it = it.map(_.split(' '))

        val stack = new mutable.Stack[(Int, HeadsToFeature)]()

        override def hasNext: Boolean = {
          val ret = stack.nonEmpty || it.hasNext
          if (!ret && !closed) {
            source.close()
            closed = true
            Logger.logss("[B&K] done loading entity features")
          }
          ret
        }

        override def next(): (Int, ((String, String), String)) = {
          if (stack.isEmpty) {
            val parts = _it.next()
            assert(parts.length == simpleKs.length + posKs.length + 2)
            if (simpleIdx.isDefined) stack.push((HeadPairFeatureStore.EntitySimpleFeatureIdx, (parts(0), parts(1)) -> parts(simpleIdx.get)))
            if (posIdx.isDefined) stack.push((HeadPairFeatureStore.EntityPosFeatureIdx, (parts(0), parts(1)) -> parts(posIdx.get)))
          }

          stack.pop()
        }
      }
    }
  }

  private class MatchStoreFeatureProvider(config: CorefSystemConfiguration) extends StoreFeatureProvider {
    override def getFeature(h1: String, h2: String): Option[String] = HeadPairFeatureStore.getFeature(HeadPairFeatureStore.EntitySimpleFeatureIdx, (h1, h2), loadFeatures(config))
  }

  private class DominantPosStoreFeatureProvider(config: CorefSystemConfiguration) extends StoreFeatureProvider {
    override def getFeature(h1: String, h2: String): Option[String] = HeadPairFeatureStore.getFeature(HeadPairFeatureStore.EntityPosFeatureIdx, (h1, h2), loadFeatures(config))
  }

  private class MatchDatabaseFeatureProvider(configuration: CorefSystemConfiguration) extends FeatureProvider {
    val path = configuration.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("Entity feature used without using precomputed " +
      "features")
    val simpleK = configuration.getAdditionalProperty(getClass, simpleMatchKOption, -1).asInstanceOf[Int]
    val posK = configuration.getAdditionalProperty(getClass, posMatchKOption, -1).asInstanceOf[Int]
    val con = new DatabaseConnection
    con.openConnection("jdbc:sqlite:" + path, null, null, "org.sqlite.JDBC")


    override def getFeature(h1: String, h2: String): Option[String] = {
      val sql = "SELECT t.`matches` FROM bkEntityMatch as t INNER JOIN strtable as s1 ON t.left == s1.entry INNER JOIN strtable as s2 ON t.right == s2.entry WHERE s1.entry = ? AND s2.entry = ? AND t.k = ?"
      val ps = con.getConnection.prepareStatement(sql)
      ps.setString(1, h1)
      ps.setString(2, h2)
      ps.setInt(3, simpleK)

      val set = ps.executeQuery()
      var value: Option[String] = None
      if (set.next()) {
        value = Some(set.getBoolean(1).toString)
      }
      ps.close()
      value
    }
  }

  private class DominantPosDatabaseFeatureProvider(configuration: CorefSystemConfiguration) extends FeatureProvider {
    val path = configuration.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String]
    if (path.isEmpty) throw new IllegalStateException("Entity feature used without using precomputed " +
      "features")
    val simpleK = configuration.getAdditionalProperty(getClass, simpleMatchKOption, -1).asInstanceOf[Int]
    val posK = configuration.getAdditionalProperty(getClass, posMatchKOption, -1).asInstanceOf[Int]
    val con = new DatabaseConnection
    con.openConnection("jdbc:sqlite:" + path, null, null, "org.sqlite.JDBC")


    override def getFeature(h1: String, h2: String): Option[String] = {
      val sql = "SELECT t.`pos` FROM bkEntityDominantPos as t INNER JOIN strtable as s1 ON t.left == s1.entry INNER JOIN strtable as s2 ON t.right == s2.entry WHERE s1.entry = ? AND s2.entry = ? AND t.k = ?"
      val ps = con.getConnection.prepareStatement(sql)
      ps.setString(1, h1)
      ps.setString(2, h2)
      ps.setInt(3, posK)

      val set = ps.executeQuery()
      var value: Option[String] = None
      if (set.next()) {
        value = Some(set.getString(1))
      }
      ps.close()
      value
    }
  }

  var matchProvider: FeatureProvider = null
  var dominantPosProvider: FeatureProvider = null

  def getSeedMatchFeature(config: CorefSystemConfiguration, m1: Mention, m2: Mention): String = {
    if (matchProvider == null) matchProvider = if (config.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String].endsWith(".db")) new MatchDatabaseFeatureProvider(config) else new MatchStoreFeatureProvider(config)
    val h1 = m1.headString
    val h2 = m2.headString
    matchProvider.getFeature(h1, h2).getOrElse(throw new IllegalStateException(s"Precomputed features for entity seeds did not contain pair $h1 $h2"))
  }

  def getDominantPosFeature(config: CorefSystemConfiguration, m1: Mention, m2: Mention): String = {
    if (dominantPosProvider == null) dominantPosProvider = if (config.getAdditionalProperty(getClass, FeatureFileOption, "").asInstanceOf[String].endsWith(".db")) new DominantPosDatabaseFeatureProvider(config) else new DominantPosStoreFeatureProvider(config)
    val h1 = m1.headString
    val h2 = m2.headString
    dominantPosProvider.getFeature(h1, h2).getOrElse(throw new IllegalStateException(s"Precomputed features for entity seeds did not contain pair $h1 $h2"))
  }
}
