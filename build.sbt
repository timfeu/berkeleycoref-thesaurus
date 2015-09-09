import AssemblyKeys._

// put this at the top of the file

name := "berkeleycoref"

organization := "org.jobimtext"

description := "Modification of the Berkeley Coreference Resolution System that provides a Apache UIMA analysis " +
  "engine" +
  " and access to distributional thesauri by the JoBimText project."

version := "1.0-SNAPSHOT"
// add the following repository if you want to use the system from within Maven; also uncomment futile and berkeleyparser
// dependencies below
//resolvers += "Bintray futile repository" at "http://dl.bintray.com/grubeninspekteur/maven"

scalaVersion := "2.11.0"

scalacOptions += "-target:jvm-1.6"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

pomExtra :=
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <source>1.6</source>
          <target>1.6</target>
        </configuration>
      </plugin>
    </plugins>
  </build>

mainClass in assembly := Some("edu.berkeley.nlp.coref.Driver")

packSettings

packMain := Map("berkeleycoref" -> "edu.berkeley.nlp.coref.Driver")

//libraryDependencies += "edu.berkeley.nlp" % "futile" % "1.0"
//
//libraryDependencies += "edu.berkeley.nlp" % "berkeleyparser" % "1.7"
//
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.1"

libraryDependencies += "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.api.coref-asl" % "1.6.2"

libraryDependencies += "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.api.resources-asl" % "1.6.2"

libraryDependencies += "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.api.syntax-asl" % "1.6.2"

libraryDependencies += "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.api.ner-asl" % "1.6.2"

libraryDependencies += "org.apache.uima" % "uimafit-core" % "2.1.0"

libraryDependencies += "com.carrotsearch" % "hppc" % "0.6.0"

libraryDependencies += "edu.stanford.nlp" % "stanford-parser" % "3.2.0"
//
//libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.34"
//
//libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.7.2"
//
/**
 * TEST dependencies
 */

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test"

libraryDependencies += "org.scalamock" %% "scalamock-scalatest-support" % "3.1.4" % "test"

libraryDependencies += "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.tokit-asl" % "1.6.2" % "test"