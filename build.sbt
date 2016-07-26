import com.github.jenshaase.uimascala.UimaSbtPlugin._

organization := "com.consetto"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.github.jenshaase.uimascala" %% "stanfordsegmenter" % "0.6.1",
  "com.github.jenshaase.uimascala" %% "stanfordpostagger" % "0.6.1",
  "com.github.jenshaase.uimascala" %% "stanfordner" % "0.6.1",
  "com.github.jenshaase.uimascala" %% "stanfordparser" % "0.6.1",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" classifier "models-german",
  "org.jsoup" % "jsoup" % "1.9.2",
  "org.apache.lucene" % "lucene-core" % "6.1.0",
  "org.apache.lucene" % "lucene-analyzers-common" % "6.1.0",
  "com.github.tototoshi" %% "scala-csv" % "1.3.3",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "io.argonaut" %% "argonaut" % "6.1" ,
  "co.fs2" %% "fs2-io" % "0.9.0-M5",
  "io.argonaut" %% "argonaut" % "6.1"
)

resolvers += "Sonatype OSS Releases"  at "https://oss.sonatype.org/content/repositories/releases/"

fork in run := true

javaOptions += "-Xmx2G"

uimaScalaSettings
