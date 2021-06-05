package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import eu.timepit.refined.refineMV

import java.time.Instant
import scala.util.Random

class FapiE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  val config: BinanceConfig = BinanceConfig(
    scheme = "https",
    host = "testnet.binancefuture.com",
    port = 443,
    infoUrl = "/fapi/v1/exchangeInfo",
    apiKey = sys.env("FAPI_API_KEY"),
    apiSecret = sys.env("FAPI_SECRET_KEY")
  )

  "getPrices" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.getPrices())
      .asserting(_ shouldNot be(empty))
  }

  "getPrice" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.getPrice(symbol = "BTCUSDT"))
      .asserting(_ shouldBe a[Price])
  }

  "getBalance" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.getBalance())
      .asserting(_ shouldBe a[FutureAccountInfoResponse])
  }

  "getKLines" in {
    val now = Instant.now()
    BinanceClient
      .createFutureClient[IO](config)
      .use(
        _.getKLines(common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100)).compile.toList
      )
      .asserting(_ shouldBe a[List[_]])
  }

  "changePositionMode" ignore {
    BinanceClient
      .createFutureClient[IO](config)
      .use(
        _.changePositionMode(true)
      )
      .asserting(_ shouldBe ())
  }

  "changeInitialLeverage" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(
        _.changeInitialLeverage(ChangeInitialLeverageParams(symbol = "BTCUSDT", leverage = refineMV(1)))
      )
      .asserting(x => (x.leverage.value, x.symbol) shouldBe (1, "BTCUSDT"))
  }

  "createOrder" in {
    val side = Random.shuffle(OrderSide.values).head
    BinanceClient
      .createFutureClient[IO](config)
      .use(
        _.createOrder(
          FutureOrderCreateParams.MARKET(
            symbol = "XRPUSDT",
            side = side,
            positionSide = FuturePositionSide.BOTH,
            quantity = 20
          )
        )
      )
      .asserting(_ shouldBe a[FutureOrderCreateResponse])
  }
}
