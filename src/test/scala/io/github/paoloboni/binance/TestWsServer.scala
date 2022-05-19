/*
 * Copyright (c) 2022 Paolo Boni
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
import cats.effect.implicits.effectResourceOps
import fs2.concurrent.SignallingRef
import fs2.{Pipe, Stream, concurrent}
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame

class TestWsServer[F[_]] private (
    val port: Int,
    terminateWhen: concurrent.Signal[F, Boolean]
)(implicit F: Async[F])
    extends Http4sDsl[F] {
  private def routes(toClient: Stream[F, WebSocketFrame])(wsb: WebSocketBuilder[F]): HttpRoutes[F] =
    HttpRoutes.of[F] { case GET -> Root / "ws" / _ =>
      val fromClient: Pipe[F, WebSocketFrame, Unit] = _.evalMap(message => F.delay(println("received: " + message)))
      wsb.build(toClient, fromClient)
    }

  def stream(toClient: Stream[F, WebSocketFrame]): Stream[F, ExitCode] =
    Stream
      .eval(Ref.of[F, ExitCode](ExitCode.Success))
      .flatMap { exitWith =>
        BlazeServerBuilder[F]
          .bindHttp(port)
          .withHttpWebSocketApp(routes(toClient)(_).orNotFound)
          .serveWhile(terminateWhen, exitWith)
      }
}

object TestWsServer {
  def apply[F[_]](port: Int)(implicit F: Async[F]): Resource[F, TestWsServer[F]] =
    SignallingRef.of[F, Boolean](false).toResource.flatMap { stopSignal =>
      Resource.make[F, TestWsServer[F]](F.delay(new TestWsServer[F](port, stopSignal)))(_ => stopSignal.set(true))
    }
}
