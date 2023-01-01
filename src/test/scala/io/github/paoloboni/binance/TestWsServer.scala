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

import cats.effect._
import com.comcast.ip4s.Port
import fs2.{Pipe, Stream}
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame

class TestWsServer[F[_]](val port: Int)(implicit F: Async[F]) extends Http4sDsl[F] {
  private def routes(toClient: Stream[F, WebSocketFrame])(wsb: WebSocketBuilder[F]): HttpRoutes[F] = {
    val fromClient: Pipe[F, WebSocketFrame, Unit] =
      _.evalMap(message => F.delay(println("received: " + message)))
    val res = wsb.build(toClient, fromClient)
    HttpRoutes.of[F] {
      case GET -> Root / "ws" / _ => res
      case GET -> Root / "stream" => res
    }
  }

  def create(toClient: Stream[F, WebSocketFrame]): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withPort(Port.fromInt(port).get)
      .withHttpWebSocketApp(routes(toClient)(_).orNotFound)
      .build
}
