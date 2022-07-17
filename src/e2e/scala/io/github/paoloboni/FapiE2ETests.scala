package io.github.paoloboni

import cats.effect.{IO, Resource}
import cats.implicits._
import io.github.paoloboni.binance._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.fapi.parameters._

import java.time.Instant
import scala.util.Random

object FapiE2ETests extends BaseE2ETest[FutureApi[IO]] {

  val config: FapiConfig = FapiConfig.Default(
    apiKey = sys.env("FAPI_API_KEY"),
    apiSecret = sys.env("FAPI_SECRET_KEY"),
    testnet = true,
    recvWindow = 20000
  )

  val sharedResource: Resource[IO, FutureApi[IO]] = BinanceClient.createFutureClient[IO](config)

  test("getPrices") { _.getPrices().map(res => expect(res.nonEmpty)) }

  test("getPrice") { _.getPrice(symbol = "BTCUSDT").map(succeed) }

  test("getBalance") { _.getBalance().map(succeed) }

  test("getKLines") { client =>
    val now = Instant.now()
    client
      .getKLines(common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100))
      .compile
      .toList
      .map(succeed)
  }

  test("changePositionMode") { _ => ignore("not testable") }

  test("changeInitialLeverage") {
    _.changeInitialLeverage(ChangeInitialLeverageParams(symbol = "BTCUSDT", leverage = 1))
      .map(x => expect((x.leverage, x.symbol) == ((1, "BTCUSDT"))))
  }

  test("createOrder") { client =>
    val side = Random.shuffle(OrderSide.values).head
    client
      .createOrder(
        FutureOrderCreateParams.MARKET(
          symbol = "LTCUSDT",
          side = side,
          positionSide = FuturePositionSide.BOTH,
          quantity = 10
        )
      )
      .map(succeed)
  }

  test("getOrder") { client =>
    val side = Random.shuffle(OrderSide.values).head
    for {
      orderCreated <- client
        .createOrder(
          FutureOrderCreateParams.MARKET(
            symbol = "LTCUSDT",
            side = side,
            positionSide = FuturePositionSide.BOTH,
            quantity = 10
          )
        )
      _ <- client
        .getOrder(
          FutureGetOrderParams.OrderId(
            symbol = "LTCUSDT",
            orderId = orderCreated.orderId
          )
        )
    } yield success
  }

  test("cancelOrder") { client =>
    for {
      createOrderResponse <- client.createOrder(
        FutureOrderCreateParams.STOP(
          symbol = "XRPUSDT",
          side = OrderSide.BUY,
          positionSide = FuturePositionSide.BOTH,
          timeInForce = FutureTimeInForce.GTC,
          quantity = 10,
          stopPrice = 2,
          price = 1.8
        )
      )

      _ <- client.cancelOrder(
        FutureOrderCancelParams(
          symbol = "XRPUSDT",
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      )
    } yield success
  }

  test("cancelAllOrders") { client =>
    for {
      _ <- client.createOrder(
        FutureOrderCreateParams.LIMIT(
          symbol = "XRPUSDT",
          side = OrderSide.SELL,
          positionSide = FuturePositionSide.BOTH,
          timeInForce = FutureTimeInForce.GTC,
          quantity = 10,
          price = 1
        )
      )

      _ <- client.cancelAllOrders(
        FutureOrderCancelAllParams(symbol = "XRPUSDT")
      )
    } yield success
  }

  test("aggregateTradeStreams") {
    _.aggregateTradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  }

  test("kLineStreams") {
    _.kLineStreams("btcusdt", Interval.`1m`)
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  }

  test("contractKLineStreams") {
    _.contractKLineStreams("btcusdt", FutureContractType.PERPETUAL, Interval.`1m`)
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  }

  test("markPriceStream") {
    _.markPriceStream("btcusdt")
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  }

  test("markPriceStream all symbols") {
    _.markPriceStream()
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  }
}
