name := "binance-scala-client"

lazy val scala212               = "2.12.13"
lazy val scala213               = "2.13.5"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalafmtOnCompile := false
ThisBuild / organization := "io.github.paoloboni"

lazy val root = (project in file("."))
  .settings(
    scalaVersion := scala213,
    releaseCrossBuild := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-core"          % "0.12.3",
      "io.circe"               %% "circe-generic"       % "0.12.3",
      "co.fs2"                 %% "fs2-core"            % "3.0.3",
      "org.typelevel"          %% "cats-core"           % "2.6.1",
      "org.typelevel"          %% "cats-effect"         % "3.1.1",
      "io.laserdisc"           %% "log-effect-core"     % "0.16.1",
      "io.laserdisc"           %% "log-effect-fs2"      % "0.16.1",
      "org.slf4j"              % "slf4j-api"            % "1.7.30",
      "org.http4s"             %% "http4s-blaze-client" % "1.0.0-M21",
      "org.http4s"             %% "http4s-circe"        % "1.0.0-M21",
      "io.lemonlabs"           %% "scala-uri"           % "2.0.0-M1",
      "com.beachape"           %% "enumeratum"          % "1.6.1",
      "com.beachape"           %% "enumeratum-circe"    % "1.5.22",
      "com.chuusai"            %% "shapeless"           % "2.3.6",
      "org.slf4j"              % "slf4j-simple"         % "1.7.30" % "test",
      "org.scalatest"          %% "scalatest"           % "3.0.9" % "test",
      "io.circe"               %% "circe-parser"        % "0.12.3" % "test",
      "com.github.tomakehurst" % "wiremock"             % "2.25.0" % "test"
    )
  )
  .enablePlugins(AutomateHeaderPlugin)

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
