ThisBuild / crossScalaVersions := Seq("2.13.8", "3.1.3")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last

ThisBuild / organization := "io.github.nafg.cloudlogging"

val noFatalWarnings = scalacOptions -= "-Xfatal-warnings"

lazy val marker =
  project
    .settings(
      noFatalWarnings,
      libraryDependencies += "io.circe" %% "circe-core" % "0.14.2",
      libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.0"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      noFatalWarnings,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.121.14-alpha",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.13" % Test
    )

publish / skip := true
