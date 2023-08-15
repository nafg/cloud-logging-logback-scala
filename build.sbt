import _root_.io.github.nafg.mergify.dsl.*

ThisBuild / crossScalaVersions := Seq("2.13.11", "3.3.0")
ThisBuild / scalaVersion       := (ThisBuild / crossScalaVersions).value.last

ThisBuild / organization := "io.github.nafg.cloudlogging"

mergifyExtraConditions := Seq(
  (Attr.Author :== "scala-steward") ||
    (Attr.Author :== "nafg-scala-steward[bot]")
)

val noFatalWarnings = scalacOptions -= "-Xfatal-warnings"

lazy val marker =
  project
    .settings(
      noFatalWarnings,
      libraryDependencies += "io.circe" %% "circe-core" % "0.14.5",
      libraryDependencies += "org.slf4j" % "slf4j-api"  % "2.0.7"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      noFatalWarnings,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.130.21-alpha",
      libraryDependencies += "org.scalatest"   %% "scalatest"                    % "3.2.16" % Test
    )

publish / skip := true
