/*
 * Copyright (c) 2023 Paolo Boni
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

import cats.effect.implicits.effectResourceOps
import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.WireMockServer
import io.github.paoloboni.binance.SharedResources.{ServerPort, rTag}
import weaver.{Expectations, GlobalRead, SimpleIOSuite, TestName}

abstract class IntegrationTest(global: GlobalRead) extends SimpleIOSuite {

  case class WebServer(http: WireMockServer, ws: TestWsServer[IO])

  def portResource: Resource[IO, ServerPort] = global.getOrFailR[ServerPort]()

  val resource: Resource[IO, WebServer] = {
    for {
      serverPort <- portResource
      port       <- serverPort.get.toResource
      http <- Resource
        .make(
          IO.delay {
            val server = new WireMockServer(port)
            server.start()
            server
          }
        )(http => IO.delay(http.stop()))
      ws = testWsServer(http)
    } yield WebServer(http, ws)
  }

  def integrationTest(name: TestName)(expectations: WebServer => IO[Expectations]): Unit =
    test(name)(resource.use(expectations))

  private def testWsServer(server: WireMockServer): TestWsServer[IO] =
    new TestWsServer[IO](port = server.port() - 1000)
}
