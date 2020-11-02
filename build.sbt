ThisBuild / crossScalaVersions := Seq("2.12.10", "2.13.1")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last

ThisBuild / organization := "io.github.nafg.cloudlogging"
ThisBuild / version := "0.3"

val noFatalWarnings = scalacOptions -= "-Xfatal-warnings"

lazy val marker =
  project
    .settings(
      noFatalWarnings,
      libraryDependencies += "io.circe" %% "circe-core" % "0.13.0",
      libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.30"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      noFatalWarnings,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.118.4-alpha",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.2" % Test
    )

publish / skip := true
