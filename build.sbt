ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "io.github.nafg.cloudlogging"
ThisBuild / version := "0.2"

val noFatalWarnings = scalacOptions -= "-Xfatal-warnings"

lazy val marker =
  project
    .settings(
      noFatalWarnings,
      libraryDependencies += "io.circe" %% "circe-core" % "0.12.3",
      libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.29"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      noFatalWarnings,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.111.0-alpha",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )

publish / skip := true
