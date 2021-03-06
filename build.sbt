import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

name := "rar-mfs"

version := "1.0.1"

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
  "org.scalanlp" %% "breeze" % "0.12",
  // native libraries are not included by default. add this if you want them (as of 0.7)
  // native libraries greatly improve performance, but increase jar sizes.
  // It also packages various blas implementations, which have licenses that may or may not
  // be compatible with the Apache License. No GPL code, as best I know.
  "org.scalanlp" %% "breeze-natives" % "0.12",
  ("nz.ac.waikato.cms.weka" % "weka-stable" % "3.8.0").exclude("nz.ac.waikato.cms.weka.thirdparty", "thirdparty"),
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "com.github.vagmcs" %% "optimus" % "2.0.0",
  "com.github.vagmcs" %% "optimus-solver-oj" % "2.0.0",
  "com.github.vagmcs" %% "optimus-solver-gurobi" % "2.0.0",
  "org.apache.commons" % "commons-lang3" % "3.4",
  "com.github.scopt" %% "scopt" % "3.5.0"
)

mainClass in assembly := Some("de.hpi.kddm.rar.Runner")

assemblyMergeStrategy in assembly := {
  case PathList("java_cup", xs@_*) => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

fork in run := true

javaOptions ++= Seq(
  "-Xmx8G"
)

resolvers ++= Seq(
  // other resolvers here
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "scm.io snapshots S3 bucket" at "https://s3-eu-central-1.amazonaws.com/maven.scm.io/snapshots/"
)