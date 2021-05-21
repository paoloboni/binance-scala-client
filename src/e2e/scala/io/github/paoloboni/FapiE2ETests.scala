package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.github.paoloboni.binance.common.BinanceConfig
import io.github.paoloboni.binance.fapi.response.GetBalance
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt

class FapiE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  val config: BinanceConfig = BinanceConfig(
    scheme = "https",
    host = "testnet.binancefuture.com",
    port = 443,
    infoUrl = "/fapi/v1/exchangeInfo",
    apiKey = sys.env("API_KEY"),
    apiSecret = sys.env("SECRET_KEY")
  )

  "getPrices" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.getPrices())
      .asserting(_ shouldNot be(empty))
  }

  "getBalance" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.getBalance())
      .asserting(_ shouldBe a[GetBalance])
  }

  "getKLines" in {
    val now = Instant.now()
    BinanceClient
      .createFutureClient[IO](config)
      .use(
        _.getKLines(common.parameters.KLines("BTCUSDT", 5.minutes, now.minusSeconds(3600), now, 100)).compile.toList
      )
      .asserting(_ shouldNot be(empty))
  }
}
