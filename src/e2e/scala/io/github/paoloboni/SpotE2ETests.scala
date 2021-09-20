package io.github.paoloboni

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import cats.implicits._
import io.github.paoloboni.binance._
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.common.{Interval, OrderSide, SpotConfig}
import io.github.paoloboni.binance.fapi.response.AggregateTradeStream
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response.{SpotAccountInfoResponse, SpotOrderCreateResponse}
import org.scalatest.LoneElement
import org.scalatest.freespec.FixtureAsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.Random

class SpotE2ETests
    extends FixtureAsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Env
    with LoneElement
    with CatsResourceIO[SpotApi[IO]] {

  val config: SpotConfig[IO] = SpotConfig.Default(
    apiKey = sys.env("SPOT_API_KEY"),
    apiSecret = sys.env("SPOT_SECRET_KEY"),
    testnet = true
  )

  val resource: Resource[IO, SpotApi[IO]] = BinanceClient.createSpotClient[IO](config)

  "getDepth" in {
    _.getDepth(common.parameters.Depth("BTCUSDT", common.parameters.DepthLimit.`500`))
      .asserting(_ shouldBe a[Depth])
  }

  "getPrices" in { _.getPrices().asserting(_ shouldNot be(empty)) }

  "getBalance" in { _.getBalance().asserting(_ shouldBe a[SpotAccountInfoResponse]) }

  "getKLines" in { client =>
    val now = Instant.now()
    client
      .getKLines(common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100))
      .compile
      .toList
      .asserting(_ shouldNot be(empty))
  }

  "createOrder" in { client =>
    val side = Random.shuffle(OrderSide.values).head
    client
      .createOrder(
        SpotOrderCreateParams.MARKET(
          symbol = "TRXUSDT",
          side = side,
          quantity = BigDecimal(50).some
        )
      )
      .asserting(_ shouldBe a[SpotOrderCreateResponse])
  }

  "cancelOrder" in { client =>
    (for {
      createOrderResponse <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = "TRXUSDT",
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 50,
          price = 0.3
        )
      )

      _ <- client.cancelOrder(
        SpotOrderCancelParams(
          symbol = "TRXUSDT",
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      )
    } yield "OK")
      .asserting(_ shouldBe "OK")
  }

  "cancelAllOrders" in { client =>
    (for {
      _ <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = "TRXUSDT",
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 50,
          price = 0.3
        )
      )

      _ <- client.cancelAllOrders(
        SpotOrderCancelAllParams(symbol = "TRXUSDT")
      )
    } yield "OK")
      .asserting(_ shouldBe "OK")
  }

  "tradeStreams" in {
    _.tradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[TradeStream])
  }

  "kLineStreams" in {
    _.kLineStreams("btcusdt", Interval.`1m`)
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[KLineStream])
  }

  "diffDepthStream" in {
    _.diffDepthStream("btcusdt")
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[DiffDepthStream])
  }

  "partialBookDepthStream" in {
    _.partialBookDepthStream("btcusdt", Level.`5`)
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[PartialDepthStream])
  }

  "allBookTickersStream" in {
    _.allBookTickersStream()
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[BookTicker])
  }

  "aggregateTradeStreams" in {
    _.aggregateTradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[AggregateTradeStream])
  }
}
