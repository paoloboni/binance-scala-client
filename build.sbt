name := "binance-scala-client"

lazy val scala212               = "2.12.13"
lazy val scala213               = "2.13.5"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalafmtOnCompile := false
ThisBuild / organization := "io.github.paoloboni"

lazy val EndToEndTest = config("e2e") extend Test
lazy val e2eSettings =
  inConfig(EndToEndTest)(Defaults.testSettings) ++
    Seq(
      EndToEndTest / fork := false,
      EndToEndTest / parallelExecution := false,
      EndToEndTest / scalaSource := baseDirectory.value / "src" / "e2e" / "scala"
    )

lazy val root = (project in file("."))
  .configs(EndToEndTest)
  .settings(e2eSettings)
  .settings(
    scalaVersion := scala213,
    releaseCrossBuild := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "io.circe"                      %% "circe-core"                    % "0.13.0",
      "io.circe"                      %% "circe-generic"                 % "0.13.0",
      "io.circe"                      %% "circe-generic-extras"          % "0.13.0",
      "io.circe"                      %% "circe-refined"                 % "0.13.0",
      "co.fs2"                        %% "fs2-core"                      % "3.0.4",
      "org.typelevel"                 %% "cats-core"                     % "2.6.1",
      "org.typelevel"                 %% "cats-effect"                   % "3.1.1",
      "io.laserdisc"                  %% "log-effect-core"               % "0.16.1",
      "io.laserdisc"                  %% "log-effect-fs2"                % "0.16.1",
      "org.slf4j"                      % "slf4j-api"                     % "1.7.30",
      "com.softwaremill.sttp.client3" %% "core"                          % "3.3.6",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % "3.3.6",
      "com.softwaremill.sttp.client3" %% "circe"                         % "3.3.6",
      "io.lemonlabs"                  %% "scala-uri"                     % "3.2.0",
      "com.beachape"                  %% "enumeratum"                    % "1.6.1",
      "com.beachape"                  %% "enumeratum-circe"              % "1.6.1",
      "com.chuusai"                   %% "shapeless"                     % "2.3.7",
      "eu.timepit"                    %% "refined"                       % "0.9.25",
      "org.slf4j"                      % "slf4j-simple"                  % "1.7.30" % "test",
      "org.scalatest"                 %% "scalatest"                     % "3.2.9"  % "test",
      "io.circe"                      %% "circe-parser"                  % "0.13.0" % "test",
      "com.github.tomakehurst"         % "wiremock"                      % "2.27.2" % "test",
      "org.typelevel"                 %% "cats-effect-testing-scalatest" % "1.1.1"  % "test"
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
