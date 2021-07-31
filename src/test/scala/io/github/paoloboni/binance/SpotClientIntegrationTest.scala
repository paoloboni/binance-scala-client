/*
 * Copyright (c) 2021 Paolo Boni
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.paoloboni.binance

import cats.effect.{Async, ExitCode, IO}
import cats.implicits._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import fs2.Stream
import io.circe.parser._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response.{SpotAccountInfoResponse, SpotFill, SpotOrderCreateResponse}
import io.github.paoloboni.binance.spot.{SpotOrderStatus, SpotOrderType, SpotTimeInForce}
import io.github.paoloboni.integration._
import io.github.paoloboni.{Env, TestAsync, TestClient}
import org.http4s.websocket.WebSocketFrame
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues._
import scodec.bits.ByteVector
import sttp.client3.UriContext

import java.time.Instant
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class SpotClientIntegrationTest extends AnyFreeSpec with Matchers with TestClient with TypeCheckedTripleEquals {

  private val wsPort = 9998

  "it should fire multiple requests when expected number of elements returned is above threshold" in new Env {
    withWiremockServer { server =>
      val from      = 1548806400000L
      val to        = 1548866280000L
      val symbol    = "ETHUSDT"
      val interval  = Interval.`1m`
      val threshold = 2

      stubInfoEndpoint(server)

      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo(from.toString),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806400000, "104.41000000", "104.43000000", "104.27000000", "104.37000000", "185.23745000", 1548806459999, "19328.98599530", 80, "62.03712000", "6475.81062590", "0"],
                          |  [1548806460000, "104.38000000", "104.40000000", "104.33000000", "104.36000000", "211.54271000", 1548806519999, "22076.70809650", 68, "175.75948000", "18342.53313250", "0"],
                          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"]
                          |]
              """.stripMargin)
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806520000"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"],
                          |  [1548836340000, "105.13000000", "105.16000000", "105.07000000", "105.10000000", "201.06821000", 1548836399999, "21139.17349190", 55, "35.40525000", "3722.13452500", "0"],
                          |  [1548836400000, "105.13000000", "105.14000000", "105.05000000", "105.09000000", "70.72517000", 1548836459999, "7432.93828700", 45, "36.68194000", "3855.32695710", "0"]
                          |]
              """.stripMargin)
          )
      )

      // NOTE: the last element in this response has timestamp equal to `to` time (from query) minus 1 second, so no further query should be performed
      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548836400000"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548836400000, "105.13000000", "105.14000000", "105.05000000", "105.09000000", "70.72517000", 1548836459999, "7432.93828700", 45, "36.68194000", "3855.32695710", "0"],
                          |  [1548866279000, "108.39000000", "108.39000000", "108.15000000", "108.22000000", "327.08359000", 1548866339999, "35415.40478090", 129, "163.42355000", "17699.38253540", "0"]
                          |]
              """.stripMargin)
          )
      )

      val config = prepareConfiguration(server)

      val result = BinanceClient
        .createSpotClient[IO](config)
        .use { gw =>
          gw
            .getKLines(
              common.parameters.KLines(
                symbol = symbol,
                interval = interval,
                startTime = Instant.ofEpochMilli(from),
                endTime = Instant.ofEpochMilli(to),
                limit = threshold
              )
            )
            .compile
            .toList
        }
        .unsafeRunSync()

      val responseFullJson = parse(
        """
          |[
          |  [1548806400000, "104.41000000", "104.43000000", "104.27000000", "104.37000000", "185.23745000", 1548806459999, "19328.98599530", 80, "62.03712000", "6475.81062590", "0"],
          |  [1548806460000, "104.38000000", "104.40000000", "104.33000000", "104.36000000", "211.54271000", 1548806519999, "22076.70809650", 68, "175.75948000", "18342.53313250", "0"],
          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"],
          |  [1548836340000, "105.13000000", "105.16000000", "105.07000000", "105.10000000", "201.06821000", 1548836399999, "21139.17349190", 55, "35.40525000", "3722.13452500", "0"],
          |  [1548836400000, "105.13000000", "105.14000000", "105.05000000", "105.09000000", "70.72517000", 1548836459999, "7432.93828700", 45, "36.68194000", "3855.32695710", "0"],
          |  [1548866279000, "108.39000000", "108.39000000", "108.15000000", "108.22000000", "327.08359000", 1548866339999, "35415.40478090", 129, "163.42355000", "17699.38253540", "0"]
          |]
        """.stripMargin
      ).value
      val expected = responseFullJson.as[List[KLine]].value

      result should have size 6
      result should contain theSameElementsInOrderAs expected

      server.verify(3, getRequestedFor(urlMatching("/api/v3/klines.*")))
    }
  }

  "it should be able to stream klines even with a threshold of 1" in new Env {
    withWiremockServer { server =>
      val from      = 1548806400000L
      val to        = 1548806640000L
      val symbol    = "ETHUSDT"
      val interval  = Interval.`1m`
      val threshold = 1

      stubInfoEndpoint(server)

      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo(from.toString),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806400000, "104.41000000", "104.43000000", "104.27000000", "104.37000000", "185.23745000", 1548806459999, "19328.98599530", 80, "62.03712000", "6475.81062590", "0"]
                          |]
              """.stripMargin)
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806459999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806460000, "104.38000000", "104.40000000", "104.33000000", "104.36000000", "211.54271000", 1548806519999, "22076.70809650", 68, "175.75948000", "18342.53313250", "0"]
                          |]
              """.stripMargin)
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806519999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"]
                          |]
              """.stripMargin)
          )
      )

      // NOTE: the last element in this response has timestamp equal to `to` time (from query) minus 1 second, so no further query should be performed
      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806579999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806580000,"104.37000000","104.37000000","104.11000000","104.30000000","503.86391000",1548806639999,"52516.17118740",150,"275.42894000","28709.15114540","0"]
                          |]
              """.stripMargin)
          )
      )

      server.stubFor(
        get(urlPathEqualTo("/api/v3/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806639999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806640000,"104.30000000","104.35000000","104.19000000","104.27000000","251.83113000",1548806699999,"26262.34369560",93,"102.24293000","10663.18343790","0"]
                          |]
              """.stripMargin)
          )
      )

      val config = prepareConfiguration(server)

      val result = BinanceClient
        .createSpotClient[IO](config)
        .use { gw =>
          gw
            .getKLines(
              common.parameters.KLines(
                symbol = symbol,
                interval = interval,
                startTime = Instant.ofEpochMilli(from),
                endTime = Instant.ofEpochMilli(to),
                limit = threshold
              )
            )
            .compile
            .toList
        }
        .unsafeRunSync()

      val responseFullJson = parse(
        """
          |[
          | [1548806400000,"104.41000000","104.43000000","104.27000000","104.37000000","185.23745000",1548806459999,"19328.98599530",80,"62.03712000","6475.81062590","0"],
          | [1548806460000,"104.38000000","104.40000000","104.33000000","104.36000000","211.54271000",1548806519999,"22076.70809650",68,"175.75948000","18342.53313250","0"],
          | [1548806520000,"104.36000000","104.39000000","104.36000000","104.38000000","59.74736000",1548806579999,"6235.56895740",28,"37.98161000","3963.95268370","0"],
          | [1548806580000,"104.37000000","104.37000000","104.11000000","104.30000000","503.86391000",1548806639999,"52516.17118740",150,"275.42894000","28709.15114540","0"]
          |]
        """.stripMargin
      ).value
      val expected = responseFullJson.as[List[KLine]].value

      result should have size 4
      result should contain theSameElementsInOrderAs expected

      server.verify(4, getRequestedFor(urlMatching("/api/v3/klines.*")))
    }
  }

  "it should return the orderbook depth" in new Env {
    withWiremockServer { server =>
      val symbol    = "ETHUSDT"
      val threshold = 1

      stubInfoEndpoint(server)

      server.stubFor(
        get(urlPathEqualTo("/api/v3/depth"))
          .withQueryParams(
            Map(
              "symbol" -> equalTo(symbol),
              "limit"  -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |{
                          |  "lastUpdateId": 1027024,
                          |  "bids": [
                          |    ["4.00000000","431.00000000"]
                          |  ],
                          |  "asks": [
                          |    ["4.00000200","12.00000000"]
                          |  ]
                          |}
                      """.stripMargin)
          )
      )

      val config = prepareConfiguration(server)

      val result = BinanceClient
        .createSpotClient[IO](config)
        .use(_.getDepth(common.parameters.Depth(symbol = symbol, limit = threshold)))
        .unsafeRunSync()

      result shouldBe Depth(
        lastUpdateId = 1027024,
        bids = List(
          Bid(4.00000000, 431.0)
        ),
        asks = List(
          Ask(4.00000200, 12.0)
        )
      )
    }
  }

  "it should return a list of prices" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      server.stubFor(
        get("/api/v3/ticker/price")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |    {
                          |        "symbol": "ETHBTC",
                          |        "price": "0.03444300"
                          |    },
                          |    {
                          |        "symbol": "LTCBTC",
                          |        "price": "0.01493000"
                          |    }
                          |]
                      """.stripMargin)
          )
      )

      val config = prepareConfiguration(server)

      val result = BinanceClient
        .createSpotClient[IO](config)
        .use(_.getPrices())
        .unsafeRunSync()

      result should contain theSameElementsInOrderAs List(
        Price("ETHBTC", BigDecimal(0.03444300)),
        Price("LTCBTC", BigDecimal(0.01493000))
      )
    }
  }

  "it should return the balance" in withWiremockServer { server =>
    import Env.{log, runtime}

    stubInfoEndpoint(server)

    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    server.stubFor(
      get(urlPathMatching("/api/v3/account"))
        .withHeader("X-MBX-APIKEY", equalTo(apiKey))
        .withQueryParam("recvWindow", equalTo("5000"))
        .withQueryParam("timestamp", equalTo(fixedTime.toString))
        .withQueryParam("signature", equalTo("6cd35332399b004466463b9ad65a112a14f31fb9ddfd5e19bd7298fbd491dbc7"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""
                        |{
                        |  "makerCommission": 15,
                        |  "takerCommission": 15,
                        |  "buyerCommission": 0,
                        |  "sellerCommission": 0,
                        |  "canTrade": true,
                        |  "canWithdraw": true,
                        |  "canDeposit": true,
                        |  "accountType": "SPOT",
                        |  "permissions": [
                        |    "SPOT"
                        |  ],
                        |  "updateTime": 123456789,
                        |  "balances": [
                        |    {
                        |      "asset": "BTC",
                        |      "free": "4723846.89208129",
                        |      "locked": "0.00000000"
                        |    },
                        |    {
                        |      "asset": "LTC",
                        |      "free": "4763368.68006011",
                        |      "locked": "0.00000001"
                        |    }
                        |  ]
                        |}
                      """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    implicit val async: Async[IO] = new TestAsync(onRealtime = fixedTime.millis)

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(_.getBalance())
      .unsafeRunSync()

    result shouldBe SpotAccountInfoResponse(
      balances = List(
        BinanceBalance("BTC", BigDecimal("4723846.89208129"), BigDecimal("0.00000000")),
        BinanceBalance("LTC", BigDecimal("4763368.68006011"), BigDecimal("0.00000001"))
      ),
      makerCommission = 15,
      takerCommission = 15,
      buyerCommission = 0,
      sellerCommission = 0,
      canTrade = true,
      canWithdraw = true,
      canDeposit = true,
      updateTime = 123456789L,
      accountType = "SPOT",
      permissions = List("SPOT")
    )
  }

  "it should create an order" in withWiremockServer { server =>
    import Env.{log, runtime}
    stubInfoEndpoint(server)

    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    server.stubFor(
      post(urlPathMatching("/api/v3/order"))
        .withHeader("X-MBX-APIKEY", equalTo(apiKey))
        .withQueryParam("recvWindow", equalTo("5000"))
        .withQueryParam("timestamp", equalTo(fixedTime.toString))
        .withQueryParam("signature", equalTo("1fdf1f9299acbc36b7d4db23bea2e295c5fda706fc9f7f47e51c771c34a2e3bd"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withBody("""{
                        |  "symbol": "BTCUSDT",
                        |  "orderId": 28,
                        |  "orderListId": -1,
                        |  "clientOrderId": "6gCrw2kRUAF9CvJDGP16IP",
                        |  "transactTime": 1507725176595,
                        |  "price": "0.00000000",
                        |  "origQty": "10.00000000",
                        |  "executedQty": "10.00000000",
                        |  "cummulativeQuoteQty": "10.00000000",
                        |  "status": "FILLED",
                        |  "timeInForce": "GTC",
                        |  "type": "MARKET",
                        |  "side": "SELL",
                        |  "fills": [
                        |    {
                        |      "price": "4000.00000000",
                        |      "qty": "1.00000000",
                        |      "commission": "4.00000000",
                        |      "commissionAsset": "USDT"
                        |    }
                        |  ]
                        |}
                      """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    implicit val async: Async[IO] = new TestAsync(onRealtime = fixedTime.millis)

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(
        _.createOrder(
          SpotOrderCreateParams.MARKET(
            symbol = "BTCUSDT",
            side = OrderSide.BUY,
            quantity = BigDecimal(10.5).some
          )
        )
      )
      .unsafeRunSync()

    result should ===(
      SpotOrderCreateResponse(
        orderId = 28L,
        symbol = "BTCUSDT",
        orderListId = -1,
        clientOrderId = "6gCrw2kRUAF9CvJDGP16IP",
        transactTime = 1507725176595L,
        price = 0,
        origQty = 10,
        executedQty = 10,
        cummulativeQuoteQty = 10,
        status = SpotOrderStatus.FILLED,
        timeInForce = SpotTimeInForce.GTC,
        `type` = SpotOrderType.MARKET,
        side = OrderSide.SELL,
        fills = List(
          SpotFill(price = 4000, qty = 1, commission = 4, commissionAsset = "USDT")
        )
      )
    )
  }

  "it should cancel an order" in withWiremockServer { server =>
    import Env.{log, runtime}
    stubInfoEndpoint(server)

    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    server.stubFor(
      delete(urlPathMatching("/api/v3/order"))
        .withHeader("X-MBX-APIKEY", equalTo(apiKey))
        .withQueryParam("recvWindow", equalTo("5000"))
        .withQueryParam("timestamp", equalTo(fixedTime.toString))
        .withQueryParam("signature", equalTo("31419491a08b991dab525300c890f2488e039199eb55c1e6a5c5367b9fedc5b0"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withBody("""
                        |{
                        |  "symbol": "LTCBTC",
                        |  "origClientOrderId": "myOrder1",
                        |  "orderId": 4,
                        |  "orderListId": -1,
                        |  "clientOrderId": "cancelMyOrder1",
                        |  "price": "2.00000000",
                        |  "origQty": "1.00000000",
                        |  "executedQty": "0.00000000",
                        |  "cummulativeQuoteQty": "0.00000000",
                        |  "status": "CANCELED",
                        |  "timeInForce": "GTC",
                        |  "type": "LIMIT",
                        |  "side": "BUY"
                        |}
                      """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    implicit val async: Async[IO] = new TestAsync(onRealtime = fixedTime.millis)

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(client =>
        for {
          _ <- client.cancelOrder(
            SpotOrderCancelParams(symbol = "BTCUSDT", orderId = 1L.some, origClientOrderId = None)
          )
        } yield "OK"
      )
      .unsafeRunSync()

    result shouldBe "OK"

  }

  "it should cancel all orders" in withWiremockServer { server =>
    import Env.{log, runtime}
    stubInfoEndpoint(server)

    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    server.stubFor(
      delete(urlPathMatching("/api/v3/openOrders"))
        .withHeader("X-MBX-APIKEY", equalTo(apiKey))
        .withQueryParam("recvWindow", equalTo("5000"))
        .withQueryParam("timestamp", equalTo(fixedTime.toString))
        .withQueryParam("signature", equalTo("5634d2bdfb9b723e6df85c1551c13acb90b0836c218bc8a08c597eba3f1563e7"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withBody("""
                        |[
                        |  {
                        |    "symbol": "BTCUSDT",
                        |    "origClientOrderId": "E6APeyTJvkMvLMYMqu1KQ4",
                        |    "orderId": 11,
                        |    "orderListId": -1,
                        |    "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |    "price": "0.089853",
                        |    "origQty": "0.178622",
                        |    "executedQty": "0.000000",
                        |    "cummulativeQuoteQty": "0.000000",
                        |    "status": "CANCELED",
                        |    "timeInForce": "GTC",
                        |    "type": "LIMIT",
                        |    "side": "BUY"
                        |  },
                        |  {
                        |    "symbol": "BTCUSDT",
                        |    "origClientOrderId": "A3EF2HCwxgZPFMrfwbgrhv",
                        |    "orderId": 13,
                        |    "orderListId": -1,
                        |    "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |    "price": "0.090430",
                        |    "origQty": "0.178622",
                        |    "executedQty": "0.000000",
                        |    "cummulativeQuoteQty": "0.000000",
                        |    "status": "CANCELED",
                        |    "timeInForce": "GTC",
                        |    "type": "LIMIT",
                        |    "side": "BUY"
                        |  },
                        |  {
                        |    "orderListId": 1929,
                        |    "contingencyType": "OCO",
                        |    "listStatusType": "ALL_DONE",
                        |    "listOrderStatus": "ALL_DONE",
                        |    "listClientOrderId": "2inzWQdDvZLHbbAmAozX2N",
                        |    "transactionTime": 1585230948299,
                        |    "symbol": "BTCUSDT",
                        |    "orders": [
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "orderId": 20,
                        |        "clientOrderId": "CwOOIPHSmYywx6jZX77TdL"
                        |      },
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "orderId": 21,
                        |        "clientOrderId": "461cPg51vQjV3zIMOXNz39"
                        |      }
                        |    ],
                        |    "orderReports": [
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "origClientOrderId": "CwOOIPHSmYywx6jZX77TdL",
                        |        "orderId": 20,
                        |        "orderListId": 1929,
                        |        "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |        "price": "0.668611",
                        |        "origQty": "0.690354",
                        |        "executedQty": "0.000000",
                        |        "cummulativeQuoteQty": "0.000000",
                        |        "status": "CANCELED",
                        |        "timeInForce": "GTC",
                        |        "type": "STOP_LOSS_LIMIT",
                        |        "side": "BUY",
                        |        "stopPrice": "0.378131",
                        |        "icebergQty": "0.017083"
                        |      },
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "origClientOrderId": "461cPg51vQjV3zIMOXNz39",
                        |        "orderId": 21,
                        |        "orderListId": 1929,
                        |        "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |        "price": "0.008791",
                        |        "origQty": "0.690354",
                        |        "executedQty": "0.000000",
                        |        "cummulativeQuoteQty": "0.000000",
                        |        "status": "CANCELED",
                        |        "timeInForce": "GTC",
                        |        "type": "LIMIT_MAKER",
                        |        "side": "BUY",
                        |        "icebergQty": "0.639962"
                        |      }
                        |    ]
                        |  }
                        |]
                        """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    implicit val async: Async[IO] = new TestAsync(onRealtime = fixedTime.millis)

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(client =>
        for {
          _ <- client.cancelAllOrders(
            SpotOrderCancelAllParams(symbol = "BTCUSDT")
          )
        } yield "OK"
      )
      .unsafeRunSync()

    result shouldBe "OK"

  }

  "it should stream trades" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      val config = prepareConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = wsPort)

      val toClient: Stream[IO, WebSocketFrame] = Stream(
        WebSocketFrame.Text("""{
                              |  "e": "trade",
                              |  "E": 123456789,
                              |  "s": "BNBBTC",
                              |  "t": 12345,
                              |  "p": "0.001",
                              |  "q": "100",
                              |  "b": 88,
                              |  "a": 50,
                              |  "T": 123456785,
                              |  "m": true,
                              |  "M": true
                              |}""".stripMargin),
        WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
      )

      val test = for {
        s <- new TestWsServer[IO](toClient)(port = wsPort).stream.compile.drain.as(ExitCode.Success).start
        result <- BinanceClient
          .createSpotClient[IO](config)
          .use(_.tradeStreams("btcusdt").compile.toList)
        _ <- s.cancel
      } yield result

      test.timeout(30.seconds).unsafeRunSync() should contain only TradeStream(
        e = "trade",
        E = 123456789,
        s = "BNBBTC",
        t = 12345,
        p = 0.001,
        q = 100,
        b = 88,
        a = 50,
        T = 123456785,
        m = true,
        M = true
      )
    }
  }

  "it should stream KLines" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      val config = prepareConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = wsPort)

      val toClient: Stream[IO, WebSocketFrame] = Stream(
        WebSocketFrame.Text("""{
                              |  "e": "kline",
                              |  "E": 123456789,
                              |  "s": "BTCUSDT",
                              |  "k": {
                              |    "t": 123400000,
                              |    "T": 123460000,
                              |    "s": "BTCUSDT",
                              |    "i": "1m",
                              |    "f": 100,
                              |    "L": 200,
                              |    "o": "0.0010",
                              |    "c": "0.0020",
                              |    "h": "0.0025",
                              |    "l": "0.0015",
                              |    "v": "1000",
                              |    "n": 100,
                              |    "x": false,
                              |    "q": "1.0000",
                              |    "V": "500",
                              |    "Q": "0.500",
                              |    "B": "123456"
                              |  }
                              |}""".stripMargin),
        WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
      )

      val test = for {
        s <- new TestWsServer[IO](toClient)(port = wsPort).stream.compile.drain.as(ExitCode.Success).start
        result <- BinanceClient
          .createSpotClient[IO](config)
          .use(_.kLineStreams("btcusdt", Interval.`1m`).compile.toList)
        _ <- s.cancel
      } yield result

      test.timeout(30.seconds).unsafeRunSync() should contain only KLineStream(
        e = "kline",
        E = 123456789L,
        s = "BTCUSDT",
        k = KLineStreamPayload(
          t = 123400000,
          T = 123460000,
          s = "BTCUSDT",
          i = Interval.`1m`,
          f = 100,
          L = 200,
          o = 0.0010,
          c = 0.0020,
          h = 0.0025,
          l = 0.0015,
          v = 1000,
          n = 100,
          x = false,
          q = 1.0000,
          V = 500,
          Q = 0.500
        )
      )
    }
  }

  "it should stream Diff. Depth" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      val config = prepareConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = wsPort)

      val toClient: Stream[IO, WebSocketFrame] = Stream(
        WebSocketFrame.Text("""{
                              |  "e": "depthUpdate",
                              |  "E": 123456789,
                              |  "s": "BNBBTC",
                              |  "U": 157,
                              |  "u": 160,
                              |  "b": [
                              |    [
                              |      "0.0024",
                              |      "10"           
                              |    ]
                              |  ],
                              |  "a": [
                              |    [
                              |      "0.0026",
                              |      "100"          
                              |    ]
                              |  ]
                              |}""".stripMargin),
        WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
      )

      val test = for {
        s <- new TestWsServer[IO](toClient)(port = wsPort).stream.compile.drain.as(ExitCode.Success).start
        result <- BinanceClient
          .createSpotClient[IO](config)
          .use(_.diffDepthStream("bnbbtc").compile.toList)
        _ <- s.cancel
      } yield result

      test.timeout(30.seconds).unsafeRunSync() should contain only DiffDepthStream(
        e = "depthUpdate",
        E = 123456789L,
        s = "BNBBTC",
        U = 157L,
        u = 160L,
        b = Seq(Bid(0.0024, 10)),
        a = Seq(Ask(0.0026, 100))
      )
    }
  }

  "it should stream Book Tickers" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      val config = prepareConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = wsPort)

      val toClient: Stream[IO, WebSocketFrame] = Stream(
        WebSocketFrame.Text("""{
                              |  "u":400900217,
                              |  "s":"BNBUSDT",
                              |  "b":"25.35190000",
                              |  "B":"31.21000000",
                              |  "a":"25.36520000",
                              |  "A":"40.66000000"
                              |}""".stripMargin),
        WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
      )

      val test = for {
        s <- new TestWsServer[IO](toClient)(port = wsPort).stream.compile.drain.as(ExitCode.Success).start
        result <- BinanceClient
          .createSpotClient[IO](config)
          .use(_.allBookTickersStream().compile.toList)
        _ <- s.cancel
      } yield result

      test.timeout(30.seconds).unsafeRunSync() should contain only BookTicker(
        u = 400900217L,
        s = "BNBUSDT",
        b = 25.35190000,
        B = 31.21000000,
        a = 25.36520000,
        A = 40.66000000
      )
    }
  }

  "it should stream Partial Book Depth streams" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      val config = prepareConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = wsPort)

      val toClient: Stream[IO, WebSocketFrame] = Stream(
        WebSocketFrame.Text("""{
                              |  "lastUpdateId": 160,
                              |  "bids": [
                              |    [
                              |      "0.0024",
                              |      "10"
                              |    ]
                              |  ],
                              |  "asks": [
                              |    [
                              |      "0.0026",
                              |      "100"
                              |    ]
                              |  ]
                              |}""".stripMargin),
        WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
      )

      val test = for {
        s <- new TestWsServer[IO](toClient)(port = wsPort).stream.compile.drain.as(ExitCode.Success).start
        result <- BinanceClient
          .createSpotClient[IO](config)
          .use(_.partialBookDepthStream("btcusdt", Level.`5`).compile.toList)
        _ <- s.cancel
      } yield result

      test.timeout(30.seconds).unsafeRunSync() should contain only PartialDepthStream(
        lastUpdateId = 160L,
        bids = Seq(Bid(0.0024, 10)),
        asks = Seq(Ask(0.0026, 100))
      )
    }
  }

  private def stubInfoEndpoint(server: WireMockServer) = {
    server.stubFor(
      get("/api/v3/exchangeInfo")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""
                        |{
                        |  "timezone":"UTC",
                        |  "serverTime":1621543436177,
                        |  "rateLimits": [
                        |    {
                        |      "rateLimitType": "REQUEST_WEIGHT",
                        |      "interval": "MINUTE",
                        |      "intervalNum": 1,
                        |      "limit": 1200
                        |    }
                        |  ],
                        |  "exchangeFilters":[],
                        |  "symbols":[]
                        |}
                      """.stripMargin)
        )
    )
  }

  private def prepareConfiguration(
      server: WireMockServer,
      apiKey: String = "",
      apiSecret: String = "",
      wsPort: Int = 80
  ) =
    SpotConfig.Custom[IO](
      restBaseUrl = uri"http://localhost:${server.port}",
      wsBaseUrl = uri"ws://localhost:$wsPort",
      exchangeInfoUrl = uri"http://localhost:${server.port}/api/v3/exchangeInfo",
      apiKey = apiKey,
      apiSecret = apiSecret
    )
}
