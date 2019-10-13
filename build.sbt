name := "binance-scala-client"

lazy val scala212               = "2.12.10"
lazy val scala213               = "2.13.1"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalaVersion := scala213
ThisBuild / scalafmtOnCompile := false
ThisBuild / organization := "io.github.paoloboni"

lazy val root = (project in file("."))
  .settings(
    crossScalaVersions := supportedScalaVersions
  )
  .enablePlugins(AutomateHeaderPlugin)
