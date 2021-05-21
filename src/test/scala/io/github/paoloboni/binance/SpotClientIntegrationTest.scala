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

import cats.Applicative
import cats.effect.{Clock, IO}
import cats.implicits._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import io.circe.parser._
import io.github.paoloboni.binance.common.Interval._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.integration._
import io.github.paoloboni.{Env, TestClient, WithClock}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}
import shapeless.tag
import scala.jdk.CollectionConverters._

import java.time.Instant
import scala.concurrent.duration._

class SpotClientIntegrationTest extends AnyFreeSpec with Matchers with EitherValues with OptionValues with TestClient {

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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
              "interval"  -> equalTo(interval.entryName),
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
        .withQueryParam("signature", equalTo("82f4e72e95e63d666b6da651e82a701722ad8a785a169318d91f36f279c55821"))
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

    implicit val withClock: WithClock[IO] = WithClock.create(stubTimer(fixedTime))

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(_.getBalance())
      .unsafeRunSync()

    result shouldBe Map(
      tag[AssetTag]("BTC") -> Balance(BigDecimal("4723846.89208129"), BigDecimal("0.00000000")),
      tag[AssetTag]("LTC") -> Balance(BigDecimal("4763368.68006011"), BigDecimal("0.00000001"))
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
        .withRequestBody(containing("recvWindow=5000"))
        .withRequestBody(containing(s"timestamp=${fixedTime.toString}"))
        .withRequestBody(containing("signature=7f87be2e5e10817945f1ada9b33cec59e03a9920e30c03e9cc7e6d6ee61fef08"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withBody("""
                        |{
                        |  "symbol": "BTCUSDT",
                        |  "orderId": 28,
                        |  "clientOrderId": "6gCrw2kRUAF9CvJDGP16IP",
                        |  "transactTime": 1507725176595
                        |}
                      """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    implicit val withClock: WithClock[IO] = WithClock.create(stubTimer(fixedTime))

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(
        _.createOrder(
          spot.parameters.SpotOrderCreationParams(
            symbol = "BTCUSDT",
            side = OrderSide.BUY,
            `type` = spot.OrderType.MARKET,
            timeInForce = None,
            quantity = 10.5,
            price = None,
            newClientOrderId = None,
            stopPrice = None,
            icebergQty = None,
            newOrderRespType = None
          )
        )
      )
      .unsafeRunSync()

    result shouldBe tag[OrderIdTag](28L)
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
        .withRequestBody(containing("recvWindow=5000"))
        .withRequestBody(containing(s"timestamp=${fixedTime.toString}"))
        .withRequestBody(containing("signature=f0e47195952cd7ca9877bbf8fbd845b4581c270d7e37e32dc2ad95541ccd98a7"))
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

    implicit val withClock: WithClock[IO] = WithClock.create(stubTimer(fixedTime))

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(client =>
        for {
          _ <- client.cancelOrder(
            SpotOrderCancelParams(symbol = "BTCUSDT", orderId = 1L.some, origClientOrderId = None)
          )
        } yield ()
      )
      .redeem(
        _ => false,
        _ => true
      )
      .unsafeRunSync()

    result shouldBe true

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
        .withRequestBody(containing("recvWindow=5000"))
        .withRequestBody(containing(s"timestamp=${fixedTime.toString}"))
        .withRequestBody(containing("signature=4dd01c83b23c3f059afcb6af54fbc730669d56153a400551ed1ed7f7913837ec"))
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

    implicit val withClock: WithClock[IO] = WithClock.create(stubTimer(fixedTime))

    val result = BinanceClient
      .createSpotClient[IO](config)
      .use(client =>
        for {
          _ <- client.cancelAllOrders(
            SpotOrderCancelAllParams(symbol = "BTCUSDT")
          )
        } yield ()
      )
      .redeem(
        _ => false,
        _ => true
      )
      .unsafeRunSync()

    result shouldBe true

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

  private def prepareConfiguration(server: WireMockServer, apiKey: String = "", apiSecret: String = "") =
    BinanceConfig("http", "localhost", server.port(), "/api/v3/exchangeInfo", apiKey, apiSecret)

  private def stubTimer(fixedTime: Long) = new Clock[IO] {
    override def applicative: Applicative[IO]  = ???
    override def monotonic: IO[FiniteDuration] = fixedTime.millis.pure[IO]
    override def realTime: IO[FiniteDuration]  = fixedTime.millis.pure[IO]
  }
}
