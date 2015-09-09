package org.jobimtext.coref.berkeley.bansalklein.provider

import org.jobimtext.api.db.DatabaseConnection

class SimpleDatabaseFeatureProvider(dbPath: String, tableName: String) extends FeatureProvider {
  val con = new DatabaseConnection
  con.openConnection("jdbc:sqlite:" + dbPath, null, null, "org.sqlite.JDBC")

  override def getFeature(h1: String, h2: String): Option[String] = {
    val sql = s"SELECT t.feature_value FROM $tableName as t INNER JOIN strtable as s1 ON t.left == s1.entry INNER JOIN strtable as s2 ON t.right == s2.entry WHERE s1.entry = ? AND s2.entry = ?"
    val ps = con.getConnection.prepareStatement(sql)
    ps.setString(1, h1)
    ps.setString(2, h2)

    val set = ps.executeQuery()
    var value: Option[String] = None
    if (set.next()) {
      value = Some(set.getString(1))
    }
    ps.close()
    value
  }
}
