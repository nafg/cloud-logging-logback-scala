import _root_.io.github.nafg.mergify.dsl.*

ThisBuild / crossScalaVersions := Seq("2.13.15", "3.3.4")
ThisBuild / scalaVersion       := (ThisBuild / crossScalaVersions).value.last

ThisBuild / organization := "io.github.nafg.cloudlogging"

mergifyExtraConditions := Seq(
  (Attr.Author :== "scala-steward") ||
    (Attr.Author :== "nafg-scala-steward[bot]")
)

val adjustScalacOptions = Seq(
  scalacOptions -= "-Xfatal-warnings",
  scalacOptions ++=
    (if (scalaVersion.value.startsWith("3."))
       Nil
     else
       Seq("-Xsource:3"))
)

lazy val marker =
  project
    .settings(
      adjustScalacOptions,
      libraryDependencies += "io.circe" %% "circe-core" % "0.14.10",
      libraryDependencies += "org.slf4j" % "slf4j-api"  % "2.0.16"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      adjustScalacOptions,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.131.13-alpha",
      libraryDependencies += "org.scalatest"   %% "scalatest"                    % "3.2.19" % Test
    )

publish / skip := true
