package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.response.KLineStream
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.LoneElement
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.Random

class FapiE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env with LoneElement {

  val config: FapiConfig = FapiConfig.Default(
    apiKey = sys.env("FAPI_API_KEY"),
    apiSecret = sys.env("FAPI_SECRET_KEY"),
    testnet = true
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
        _.changeInitialLeverage(ChangeInitialLeverageParams(symbol = "BTCUSDT", leverage = 1))
      )
      .asserting(x => (x.leverage, x.symbol) shouldBe (1, "BTCUSDT"))
  }

  "createOrder" in {
    val side = Random.shuffle(OrderSide.values).head
    BinanceClient
      .createFutureClient[IO](config)
      .use(
        _.createOrder(
          FutureOrderCreateParams.MARKET(
            symbol = "LTCUSDT",
            side = side,
            positionSide = FuturePositionSide.BOTH,
            quantity = 10
          )
        )
      )
      .asserting(_ shouldBe a[FutureOrderCreateResponse])
  }

  "aggregateTradeStreams" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.aggregateTradeStreams("btcusdt").take(1).compile.toList.timeout(30.seconds))
      .asserting(_.loneElement shouldBe a[AggregateTradeStream])
  }

  "kLineStreams" in {
    BinanceClient
      .createFutureClient[IO](config)
      .use(_.kLineStreams("btcusdt", Interval.`1m`).take(1).compile.toList)
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[KLineStream])
  }
}
