package io.github.paoloboni

import cats.effect.{IO, Resource}
import cats.implicits._
import io.github.paoloboni.binance._
import io.github.paoloboni.binance.common.{Interval, OrderSide, SpotConfig}
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.util.Random

object SpotLegacyE2ETests extends BaseE2ETest[SpotApi[IO]] {

  val config: SpotConfig = SpotConfig.Default(
    apiKey = sys.env("SPOT_API_KEY"),
    apiSecret = sys.env("SPOT_SECRET_KEY"),
    testnet = true,
    recvWindow = 20000
  )

  val sharedResource: Resource[IO, SpotApi[IO]] = BinanceClient.createSpotClient[IO](config)

  test("getDepth") {
    _.getDepth(common.parameters.DepthParams("BTCUSDT", common.parameters.DepthLimit.`500`))
      .map(succeed): @scala.annotation.nowarn
  }

  test("getPrices")(_.getPrices().map(res => expect(res.nonEmpty)): @scala.annotation.nowarn)

  test("getBalance")(_.getBalance().map(succeed): @scala.annotation.nowarn)

  test("getKLines") { client =>
    val now = Instant.now()
    client
      .getKLines(common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100))
      .compile
      .toList
      .map(res => expect(res.nonEmpty)): @scala.annotation.nowarn
  }

  test("createOrder") { client =>
    val side = Random.shuffle(OrderSide.values).head
    client
      .createOrder(
        SpotOrderCreateParams.MARKET(
          symbol = "TRXUSDT",
          side = side,
          quantity = BigDecimal(1000).some
        )
      )
      .map(succeed): @scala.annotation.nowarn
  }

  test("queryOrder") { client =>
    val symbol = "TRXUSDT"
    for {
      createOrderResponse <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 1000,
          price = 0.08
        )
      ): @scala.annotation.nowarn

      _ <- client.queryOrder(
        SpotOrderQueryParams(
          symbol = symbol,
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      ): @scala.annotation.nowarn
    } yield success
  }

  test("cancelOrder") { client =>
    val symbol = "TRXUSDT"
    (for {
      createOrderResponse <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 1000,
          price = 0.08
        )
      ): @scala.annotation.nowarn

      _ <- client
        .cancelOrder(
          SpotOrderCancelParams(
            symbol = symbol,
            orderId = createOrderResponse.orderId.some,
            origClientOrderId = None
          )
        ): @scala.annotation.nowarn
    } yield success).retryWithBackoff(initialDelay = Duration.Zero)
  }

  test("cancelAllOrders") { client =>
    val symbol = "TRXUSDT"
    (for {
      _ <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 1000,
          price = 0.08
        )
      ): @scala.annotation.nowarn

      _ <- client
        .cancelAllOrders(
          SpotOrderCancelAllParams(symbol = symbol)
        ): @scala.annotation.nowarn
    } yield success).retryWithBackoff(initialDelay = Duration.Zero)
  }

}
