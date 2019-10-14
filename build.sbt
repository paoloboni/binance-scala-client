name := "binance-scala-client"

lazy val scala212               = "2.12.10"
lazy val scala213               = "2.13.1"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalaVersion := scala213
ThisBuild / scalafmtOnCompile := false
ThisBuild / organization := "io.github.paoloboni"

lazy val root = (project in file("."))
  .settings(
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-core"          % "0.12.2",
      "io.circe"               %% "circe-generic"       % "0.12.2",
      "co.fs2"                 %% "fs2-core"            % "2.0.1",
      "org.typelevel"          %% "cats-core"           % "2.0.0",
      "org.typelevel"          %% "cats-effect"         % "2.0.0",
      "org.systemfw"           %% "upperbound"          % "0.3.0",
      "io.laserdisc"           %% "log-effect-core"     % "0.11.1",
      "io.laserdisc"           %% "log-effect-fs2"      % "0.11.1",
      "org.slf4j"              % "slf4j-api"            % "1.7.28",
      "org.http4s"             %% "http4s-blaze-client" % "0.21.0-M5",
      "org.http4s"             %% "http4s-circe"        % "0.21.0-M5",
      "io.lemonlabs"           %% "scala-uri"           % "2.0.0-M1",
      "com.beachape"           %% "enumeratum"          % "1.5.13",
      "com.beachape"           %% "enumeratum-circe"    % "1.5.22",
      "com.chuusai"            %% "shapeless"           % "2.3.3",
      "org.slf4j"              % "slf4j-simple"         % "1.7.28" % "test",
      "org.scalatest"          %% "scalatest"           % "3.0.8" % "test",
      "io.circe"               %% "circe-parser"        % "0.12.2" % "test",
      "com.github.tomakehurst" % "wiremock"             % "2.25.0" % "test"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

import ReleaseTransformations._

releaseCrossBuild := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
