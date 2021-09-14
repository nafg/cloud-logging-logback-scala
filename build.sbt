ThisBuild / crossScalaVersions := Seq("2.12.14", "2.13.6")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last

ThisBuild / organization := "io.github.nafg.cloudlogging"

val noFatalWarnings = scalacOptions -= "-Xfatal-warnings"

lazy val marker =
  project
    .settings(
      noFatalWarnings,
      libraryDependencies += "io.circe" %% "circe-core" % "0.14.1",
      libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.32"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      noFatalWarnings,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.121.14-alpha",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % Test
    )

publish / skip := true
