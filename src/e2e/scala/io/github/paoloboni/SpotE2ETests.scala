package io.github.paoloboni

import cats.effect.{IO, Resource}
import cats.implicits._
import io.github.paoloboni.Env.log
import io.github.paoloboni.binance._
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.common.{Interval, OrderSide, SpotConfig}
import io.github.paoloboni.binance.fapi.response.AggregateTradeStream
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response._

import java.time.Instant
import scala.util.Random

class SpotE2ETests extends BaseE2ETest[SpotApi[IO]] {

  val config: SpotConfig[IO] = SpotConfig.Default(
    apiKey = sys.env("SPOT_API_KEY"),
    apiSecret = sys.env("SPOT_SECRET_KEY"),
    testnet = true
  )

  val resource: Resource[IO, SpotApi[IO]] = BinanceClient.createSpotClient[IO](config)

  "getDepth" in {
    _.getDepth(common.parameters.DepthParams("BTCUSDT", common.parameters.DepthLimit.`500`))
      .asserting(_ shouldBe a[DepthGetResponse])
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
          quantity = BigDecimal(200).some
        )
      )
      .asserting(_ shouldBe a[SpotOrderCreateResponse])
  }

  "queryOrder" in { client =>
    val symbol = "LTCUSDT"
    (for {
      orderPrice <- estimateOrderPrice(symbol)(client)
      createOrderResponse <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 0.1,
          price = orderPrice
        )
      )

      queryResponse <- client.queryOrder(
        SpotOrderQueryParams(
          symbol = symbol,
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      )
    } yield queryResponse)
      .asserting(_ shouldBe a[SpotOrderQueryResponse])
  }

  "cancelOrder" in { client =>
    val symbol = "LTCUSDT"
    (for {
      orderPrice <- estimateOrderPrice(symbol)(client)
      createOrderResponse <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 0.1,
          price = orderPrice
        )
      )

      _ <- client.cancelOrder(
        SpotOrderCancelParams(
          symbol = symbol,
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      )
    } yield "OK")
      .asserting(_ shouldBe "OK")
  }

  "cancelAllOrders" in { client =>
    val symbol = "LTCUSDT"
    (for {
      orderPrice <- estimateOrderPrice(symbol)(client)
      _ <- client.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 0.1,
          price = orderPrice
        )
      )

      _ <- client.cancelAllOrders(
        SpotOrderCancelAllParams(symbol = symbol)
      )
    } yield "OK")
      .asserting(_ shouldBe "OK")
  }

  "tradeStreams" in {
    _.tradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .asserting(_.loneElement shouldBe a[TradeStream])
  }

  "kLineStreams" in {
    _.kLineStreams("btcusdt", Interval.`1m`)
      .take(1)
      .compile
      .toList
      .asserting(_.loneElement shouldBe a[KLineStream])
  }

  "diffDepthStream" in {
    _.diffDepthStream("btcusdt")
      .take(1)
      .compile
      .toList
      .asserting(_.loneElement shouldBe a[DiffDepthStream])
  }

  "partialBookDepthStream" in {
    _.partialBookDepthStream("btcusdt", Level.`5`)
      .take(1)
      .compile
      .toList
      .asserting(_.loneElement shouldBe a[PartialDepthStream])
  }

  "allBookTickersStream" in {
    _.allBookTickersStream()
      .take(1)
      .compile
      .toList
      .asserting(_.loneElement shouldBe a[BookTicker])
  }

  "aggregateTradeStreams" in {
    _.aggregateTradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .asserting(_.loneElement shouldBe a[AggregateTradeStream])
  }

  private def error(msg: String): Throwable = new RuntimeException(msg)

  private def estimateOrderPrice(symbol: String)(client: FixtureParam): IO[BigDecimal] = for {
    symbolConfig  <- IO.fromOption(client.exchangeInfo.symbols.find(_.symbol == symbol))(error("Symbol not found"))
    currentPrices <- client.getPrices()
    currentPrice <- IO.fromOption(currentPrices.find(_.symbol.toUpperCase == symbol))(
      error(s"Price not found for symbol $symbol")
    )
    percentPriceFilter <- IO.fromOption(symbolConfig.filters.collectFirst { case f: PERCENT_PRICE => f })(
      error("Percent price filter not available")
    )
  } yield (currentPrice.price * percentPriceFilter.multiplierUp * 0.99)
    .setScale(symbolConfig.baseAssetPrecision, BigDecimal.RoundingMode.HALF_DOWN)
}
