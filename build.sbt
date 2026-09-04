import _root_.io.github.nafg.mergify.dsl.*
import org.typelevel.scalacoptions.ScalacOptions

ThisBuild / crossScalaVersions := Seq("2.13.18", "3.3.8")
ThisBuild / scalaVersion       := (ThisBuild / crossScalaVersions).value.last

ThisBuild / organization := "io.github.nafg.cloudlogging"

mergifyExtraConditions := Seq(
  (Attr.Author :== "scala-steward") ||
    (Attr.Author :== "nafg-scala-steward[bot]")
)

val adjustScalacOptions = Seq(
  tpolecatExcludeOptions ++= ScalacOptions.fatalWarningOptions,
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
      libraryDependencies += "io.circe" %% "circe-core" % "0.14.16",
      libraryDependencies += "org.slf4j" % "slf4j-api"  % "2.0.19"
    )

lazy val appender =
  project
    .dependsOn(marker)
    .settings(
      adjustScalacOptions,
      libraryDependencies += "com.google.cloud" % "google-cloud-logging-logback" % "0.145.0-alpha",
      libraryDependencies += "ch.qos.logback"   % "logback-classic"              % "1.6.3",
      libraryDependencies += "org.scalatest"   %% "scalatest"                    % "3.2.20" % Test
    )

publish / skip := true
