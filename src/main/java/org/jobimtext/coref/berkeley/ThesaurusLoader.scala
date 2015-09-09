package org.jobimtext.coref.berkeley

import java.sql.SQLException

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.futile.util.Logger
import org.jobimtext.api.db.{AntonymDatabase, Destroyable, DatabaseResource}
import scala.collection.mutable.ArrayBuffer
import org.jobimtext.api.struct.{IThesaurusDatastructure, DatabaseThesaurusDatastructure}
import org.jobimtext.api.configuration.DatabaseThesaurusConfiguration
import java.io.{File, StringReader}

import scala.xml.Node

/**
 * Loads thesaurus data based on a configuration file in XML format. Each thesaurus listed in the configuration file
 * will be added to the list of queried thesauri. The configuration file specifies the features to use, the database
 * connection and, optionally, the antonym database connection.
 *
 * The structure of the configuration file can be taken from the accompanied documentation. An XML schema file
 * is available in the "config" directory.
 *
 * @author Tim Feuerbach
 */
object ThesaurusLoader {

  /**
   * Loads a thesaurus collection, including database information and enabled features,
   * by parsing and interpreting the specified XML configuration file.
   *
   * @param configFile the thesaurus configuration file
   * @param config the coreference system parameters passed to the thesauri
   * @param useDummyThesaurus whether to use a dummy thesaurus interface instead of a real database
   *
   * @return the thesaurus collection
   */
  def loadThesaurusCollection(configFile: File, config: CorefSystemConfiguration, useDummyThesaurus: Boolean = false): Option[ThesaurusCollection] = {
    if (configFile == null || !configFile.exists() || !configFile.isFile) {
      Logger.warn("Ignoring non existing thesaurus configuration file: " + configFile.toString)
      return None
    }

    if (useDummyThesaurus) {
      Logger.warn("DUMMY THESAURUS enabled! Results will be empty!")
    }

    val connectedInterfaces = scala.collection.mutable.ListBuffer.empty[Destroyable]

    val root = scala.xml.XML.loadFile(configFile)

    var thesauriLoaded = 0
    var featuresEnabled = 0

    val featuresToUse = new ArrayBuffer[ThesaurusFeature]()
    val thesauri = scala.collection.mutable.Map.empty[String, DistributionalThesaurusComputer]

    for (thesaurus <- root \ "thesaurus") {

      val id = (thesaurus \ "@id").text
      if (id.isEmpty) throw new InvalidThesaurusConfigFileException("No identifier for thesaurus defined")

      val holingSystem = (thesaurus \ "holingSystem").text
      if (holingSystem.isEmpty) throw new InvalidThesaurusConfigFileException("No holing system class specified")


        val maxExpansions = {
          val maxExpansionsString = (thesaurus \ "@maxExpansions").text
          try {
          if (maxExpansionsString.nonEmpty) Some(maxExpansionsString.toInt) else None
          } catch {
            case e: NumberFormatException =>
              throw new InvalidThesaurusConfigFileException(s"maxExpansions for thesaurus $id is not an integer", e)
          }
        }


      // extract features to use for this thesaurus
      for (feature <- thesaurus \ "features" \ "feature") {
        featuresToUse += ThesaurusFeature(id, feature.text, feature.attributes.asAttrMap)
        Logger.logs(s"Enabled feature ${feature.text} with options ${feature.attributes.asAttrMap} for thesaurus $id")
        featuresEnabled += 1
      }

      val databaseThesaurusConfig = (thesaurus \ "databaseThesaurusConfiguration")(0)

      if (!useDummyThesaurus && databaseThesaurusConfig.isEmpty) {
        throw new IllegalArgumentException("Missing database thesaurus configuration in file " + configFile)
      }

      // load database interface
      val configurationObject = DatabaseThesaurusConfiguration.getFromXmlDataReader(new StringReader(databaseThesaurusConfig
        .toString()))

      if (!useDummyThesaurus && (configurationObject.getJdbcString == null || configurationObject.getDbUrl == null)) {
        throw new IllegalArgumentException("The database configuration in " + configFile + " is missing the JDBC string and/or dbUrl. Config is " + databaseThesaurusConfig)
      }

      val interface = getDatabaseConnection(configurationObject, useDummyThesaurus)
      val success = interface.connect()
      if (!success) {
        connectedInterfaces.foreach(_.destroy())
        throw new InvalidThesaurusConfigFileException(s"Could not connect to database for thesaurus $id",
          interface.getError)
      }
      connectedInterfaces += interface

      var antonymDatabase: Option[AntonymDatabase] = None

      if ((thesaurus \ "antonymDatabase").nonEmpty) {
        val node = (thesaurus \ "antonymDatabase")(0)
        val dbUrl = getTextOfDescendant(node, "dbUrl")
        val dbUser = getTextOfDescendant(node, "dbUser")
        val dbPassword = getTextOfDescendant(node, "dbPassword")
        val driver = getTextOfDescendant(node, "jdbcString")

        antonymDatabase = Some(new AntonymDatabase)
        try {
          antonymDatabase.get.connect(dbUrl, dbUser, dbPassword, driver)
        } catch {
          case e @ (_ : ClassCastException | _ : SQLException) =>
            connectedInterfaces.foreach(_.destroy())
            throw new IllegalArgumentException("Can't connect to antonym database", e)
        }
        connectedInterfaces += antonymDatabase.get
      }

      // load thesaurus specific to holing system used
      try {
        thesauri += (id -> ThesaurusFactory.createThesaurus(holingSystem, id, new ThesaurusCache(config.dtUseCache), interface, antonymDatabase, maxExpansions, config))
      } catch {
        case e: IllegalArgumentException =>
          connectedInterfaces.foreach(_.destroy())
          throw new InvalidThesaurusConfigFileException("Thesaurus configuration " +
            s"failure for $id due to unknown holing system $holingSystem", e)
      }

      thesauriLoaded += 1
    }

    Logger.logs(thesauriLoaded + " thesauri registered with " + featuresEnabled + " enabled features")

    Some(new ThesaurusCollection(thesauri.toMap, featuresToUse.toArray, connectedInterfaces))
  }

  private def getTextOfDescendant(parent: Node, descendantString: String, required: Boolean = true) = {
    val descendant = parent \ descendantString
    if (descendant.isEmpty) {
      if (required) throw new IllegalArgumentException("Missing descendant " + descendant + " for node " + " parent") else ""
    } else {
      descendant(0).text
    }
  }

  private def getDatabaseConnection(configurationObject: DatabaseThesaurusConfiguration, useDummyThesaurus: Boolean):
  DatabaseResource with IThesaurusDatastructure[String, String] = if (!useDummyThesaurus) {
    new DatabaseThesaurusDatastructure(configurationObject)
  } else {
    new DummyDatabaseThesaurusDatastructure
  }
}

class InvalidThesaurusConfigFileException(message: String, cause: Throwable = null) extends
IllegalArgumentException(message, cause) {
}
