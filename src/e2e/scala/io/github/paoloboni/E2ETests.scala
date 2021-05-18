package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.github.paoloboni.TestConfig.config
import io.github.paoloboni.binance.{BinanceClient, KLines}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt

class E2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  "getPrices" in {
    BinanceClient[IO](config)
      .use(_.getPrices())
      .asserting(_ shouldNot be(empty))
  }

  "getBalance" in {
    BinanceClient[IO](config)
      .use(_.getBalance())
      .asserting(_ shouldNot be(empty))
  }

  "getKLines" in {
    val now = Instant.now()
    BinanceClient[IO](config)
      .use(_.getKLines(KLines("BTCUSDT", 5.minutes, now.minusSeconds(3600), now, 100)).compile.toList)
      .asserting(_ shouldNot be(empty))
  }
}
