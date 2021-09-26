name := "binance-scala-client"

lazy val scala212               = "2.12.15"
lazy val scala213               = "2.13.6"
lazy val scala3                 = "3.0.2"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalafmtOnCompile := false
ThisBuild / organization      := "io.github.paoloboni"

lazy val EndToEndTest = config("e2e") extend Test
lazy val e2eSettings =
  inConfig(EndToEndTest)(Defaults.testSettings) ++
    Seq(
      EndToEndTest / fork              := false,
      EndToEndTest / parallelExecution := false,
      EndToEndTest / scalaSource       := baseDirectory.value / "src" / "e2e" / "scala"
    )

lazy val circeV             = "0.14.1"
lazy val fs2V               = "3.1.2"
lazy val catsCoreV          = "2.6.1"
lazy val catsEffectV        = "3.2.9"
lazy val log4CatsV          = "2.1.1"
lazy val slf4jV             = "1.7.32"
lazy val sttpV              = "3.3.14"
lazy val enumeratumV        = "1.7.0"
lazy val shapelessV         = "2.3.7"
lazy val scalatestV         = "3.2.10"
lazy val wiremockV          = "2.27.2"
lazy val catsEffectTestingV = "1.3.0"
lazy val http4sV            = "1.0.0-M27"
lazy val http4sBlazeV       = "0.15.2"

lazy val root = (project in file("."))
  .configs(EndToEndTest)
  .settings(e2eSettings)
  .settings(
    scalaVersion      := scala213,
    releaseCrossBuild := true,
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-Xmax-inlines", "64")
      case _            => Seq.empty
    }),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    crossScalaVersions            := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "io.circe"                      %% "circe-core"                    % circeV,
      "io.circe"                      %% "circe-generic"                 % circeV,
      "co.fs2"                        %% "fs2-core"                      % fs2V,
      "org.typelevel"                 %% "cats-core"                     % catsCoreV,
      "org.typelevel"                 %% "cats-effect"                   % catsEffectV,
      "org.typelevel"                 %% "log4cats-core"                 % log4CatsV,
      "org.typelevel"                 %% "log4cats-slf4j"                % log4CatsV,
      "org.slf4j"                      % "slf4j-api"                     % slf4jV,
      "com.softwaremill.sttp.client3" %% "core"                          % sttpV,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % sttpV,
      "com.softwaremill.sttp.client3" %% "circe"                         % sttpV,
      "io.circe"                      %% "circe-parser"                  % circeV             % "test",
      "org.slf4j"                      % "slf4j-simple"                  % slf4jV             % "test",
      "org.scalatest"                 %% "scalatest"                     % scalatestV         % "test",
      "com.github.tomakehurst"         % "wiremock"                      % wiremockV          % "test",
      "org.typelevel"                 %% "cats-effect-testing-scalatest" % catsEffectTestingV % "test",
      "org.http4s"                    %% "http4s-core"                   % http4sV            % "test",
      "org.http4s"                    %% "http4s-dsl"                    % http4sV            % "test",
      "org.http4s"                    %% "http4s-blaze-server"           % http4sV            % "test",
      "org.http4s"                    %% "http4s-circe"                  % http4sV            % "test",
      "org.http4s"                    %% "blaze-http"                    % http4sBlazeV       % "test"
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, minor)) =>
        Seq(
          "io.circe"     %% "circe-generic-extras" % circeV,
          "com.beachape" %% "enumeratum"           % enumeratumV,
          "com.beachape" %% "enumeratum-circe"     % enumeratumV,
          "com.chuusai"  %% "shapeless"            % shapelessV
        ) ++ (minor match {
          case 12 => Seq("org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0")
          case _  => Seq.empty
        })
      case _ =>
        Seq.empty
    })
  )
  .enablePlugins(AutomateHeaderPlugin, GhpagesPlugin, SiteScaladocPlugin)

git.remoteRepo := "git@github.com:paoloboni/binance-scala-client.git"

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
