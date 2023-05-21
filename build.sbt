name := "binance-scala-client"

lazy val scala212               = "2.12.17"
lazy val scala213               = "2.13.10"
lazy val scala3                 = "3.2.1"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalafmtOnCompile := false
ThisBuild / organization      := "io.github.paoloboni"

lazy val EndToEndTest = config("e2e") extend Test
lazy val e2eSettings =
  inConfig(EndToEndTest)(Defaults.testSettings) ++
    Seq(
      EndToEndTest / fork               := true,
      EndToEndTest / testForkedParallel := true,
      EndToEndTest / scalaSource        := baseDirectory.value / "src" / "e2e" / "scala"
    )

lazy val circeV             = "0.14.1"
lazy val fs2V               = "3.6.1"
lazy val catsCoreV          = "2.9.0"
lazy val catsEffectV        = "3.4.11"
lazy val log4CatsV          = "2.6.0"
lazy val slf4jV             = "2.0.7"
lazy val sttpV              = "3.8.14"
lazy val enumeratumV        = "1.7.2"
lazy val shapelessV         = "2.3.10"
lazy val wiremockV          = "2.27.2"
lazy val catsEffectTestingV = "1.4.0"
lazy val http4sV            = "1.0.0-M30"
lazy val http4sBlazeV       = "0.23.15"
lazy val weaverV            = "0.8.3"

lazy val root = (project in file("."))
  .configs(EndToEndTest)
  .settings(e2eSettings)
  .settings(
    scalaVersion      := scala213,
    releaseCrossBuild := true,
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-Xmax-inlines", "64")
      case _            => Seq("-Ywarn-unused", "-nowarn")
    }),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    crossScalaVersions            := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "io.circe"                      %% "circe-core"          % circeV,
      "io.circe"                      %% "circe-generic"       % circeV,
      "co.fs2"                        %% "fs2-core"            % fs2V,
      "org.typelevel"                 %% "cats-core"           % catsCoreV,
      "org.typelevel"                 %% "cats-effect"         % catsEffectV,
      "org.typelevel"                 %% "log4cats-core"       % log4CatsV,
      "org.typelevel"                 %% "log4cats-slf4j"      % log4CatsV,
      "org.slf4j"                      % "slf4j-api"           % slf4jV,
      "com.softwaremill.sttp.client3" %% "core"                % sttpV,
      "com.softwaremill.sttp.client3" %% "fs2"                 % sttpV,
      "com.softwaremill.sttp.client3" %% "circe"               % sttpV,
      "io.circe"                      %% "circe-parser"        % circeV       % Test,
      "org.slf4j"                      % "slf4j-simple"        % slf4jV       % Test,
      "com.github.tomakehurst"         % "wiremock"            % wiremockV    % Test,
      "org.typelevel"                 %% "cats-effect-testkit" % catsEffectV  % Test,
      "org.http4s"                    %% "http4s-core"         % http4sV      % Test,
      "org.http4s"                    %% "http4s-dsl"          % http4sV      % Test,
      "org.http4s"                    %% "http4s-ember-server" % http4sV      % Test,
      "org.http4s"                    %% "http4s-circe"        % http4sV      % Test,
      "org.http4s"                    %% "blaze-http"          % http4sBlazeV % Test,
      "com.disneystreaming"           %% "weaver-cats"         % weaverV      % Test,
      "com.disneystreaming"           %% "weaver-scalacheck"   % weaverV      % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, minor)) =>
        Seq(
          "io.circe"     %% "circe-generic-extras" % circeV,
          "com.beachape" %% "enumeratum"           % enumeratumV,
          "com.beachape" %% "enumeratum-circe"     % enumeratumV,
          "com.chuusai"  %% "shapeless"            % shapelessV
        ) ++ (minor match {
          case 12 => Seq("org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0")
          case _  => Seq.empty
        })
      case _ =>
        Seq.empty
    }),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect")
  )
  .enablePlugins(AutomateHeaderPlugin, GhpagesPlugin, SiteScaladocPlugin)

git.remoteRepo := "git@github.com:paoloboni/binance-scala-client.git"

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  runClean,
  runTest,
  publishArtifacts,
  releaseStepCommand("sonatypeReleaseAll")
)
