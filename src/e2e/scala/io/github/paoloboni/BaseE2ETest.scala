package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import io.github.paoloboni.binance.common.BinanceConfig
import org.scalatest.LoneElement
import org.scalatest.freespec.FixtureAsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{Duration, DurationInt}

abstract class BaseE2ETest[API]
    extends FixtureAsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with LoneElement
    with CatsResourceIO[API] {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  override protected val ResourceTimeout: Duration = 30.seconds

  def config: BinanceConfig.Aux[IO, API]
}
