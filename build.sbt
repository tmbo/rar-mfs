import ReleaseTransformations._

name := "rar-mfs"

version := "1.0-SNAPSHOT"

organization := "de.hpi.kddm"

organizationName := "HPI Knowledge Discovery and Data Mining Group"

organizationHomepage := Some(url("https://hpi.de/en/mueller/home.html"))

startYear := Some(2016)

sonatypeProfileName := "de.hpi.kddm"

description := "Feature selection algorithm for mixed data on large data sets"

licenses := Seq("CC BY-NC 4.0" -> url("https://creativecommons.org/licenses/by-nc/4.0/legalcode"))

homepage := Some(url("https://github.com/tmbo/rar-mfs"))

scmInfo := Some(ScmInfo(url("https://github.com/tmbo/rar-mfs"), "https://github.com/tmbo/rar-mfs.git"))

publishMavenStyle := true

pomExtra := {
  <developers>
    <developer>
      <id>tmbo</id>
      <name>Tom Bocklisch</name>
      <email>tom.bocklisch@scalableminds.com</email>
      <url>http://github.com/tmbo</url>
    </developer>
  </developers>
}

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)

// Build Settings

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.github.tototoshi" %% "scala-csv" % "1.3.0",
  "org.scalanlp" %% "breeze" % "0.12" ,
  // native libraries are not included by default. add this if you want them (as of 0.7)
  // native libraries greatly improve performance, but increase jar sizes.
  // It also packages various blas implementations, which have licenses that may or may not
  // be compatible with the Apache License. No GPL code, as best I know.
  "org.scalanlp" %% "breeze-natives" % "0.12",
  "nz.ac.waikato.cms.weka" % "weka-stable" % "3.8.0",
  "ch.qos.logback" %  "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "com.github.vagm" %% "optimus" % "2.0.0-SNAPSHOT",
  "com.github.vagm" %% "optimus-solver-gurobi" % "2.0.0-SNAPSHOT",
  "org.apache.commons" % "commons-lang3" % "3.4"
)

fork in run := true

javaOptions ++= Seq(
  "-Xmx8G"
)

resolvers ++= Seq(
  // other resolvers here
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
)