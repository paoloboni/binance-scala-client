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

package io.github.paoloboni.http

import cats.effect.kernel.Async
import cats.effect.std.Queue
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.{Pipe, Stream}
import io.circe.Decoder
import io.circe.parser.decode
import io.github.paoloboni.http.ratelimit._
import org.typelevel.log4cats.Logger
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.{BodySerializer, SttpBackend, _}
import sttp.model.Uri
import sttp.ws.WebSocketFrame

sealed class HttpClient[F[_]: Logger](implicit
    F: Async[F],
    client: SttpBackend[F, Any with Fs2Streams[F] with capabilities.WebSockets]
) {

  def get[Response](
      uri: Uri,
      responseAs: ResponseAs[Response, Any],
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  ): F[Response] = {
    val httpRequest = basicRequest
      .headers(headers)
      .get(uri)
      .response(responseAs)
    sendRequest(httpRequest, limiters, weight)
  }

  def post[Request: BodySerializer, Response](
      uri: Uri,
      requestBody: Option[Request],
      responseAs: ResponseAs[Response, Any],
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  ): F[Response] = {
    val preparedRequest = basicRequest
      .headers(headers)
      .post(uri)
      .response(responseAs)
    val httpRequest = requestBody.fold(preparedRequest)(preparedRequest.body(_))
    sendRequest(httpRequest, limiters, weight)
  }

  def delete[Response](
      uri: Uri,
      responseAs: ResponseAs[Response, Any],
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  ): F[Response] = {
    val httpRequest = basicRequest
      .headers(headers)
      .delete(uri)
      .response(responseAs)
    sendRequest(httpRequest, limiters, weight)
  }

  def ws[DataFrame: Decoder](
      uri: Uri
  ): Stream[F, DataFrame] = {
    def webSocketFramePipe(
        q: Queue[F, Option[DataFrame]]
    ): Pipe[F, WebSocketFrame.Data[_], WebSocketFrame] = { input =>
      input.evalMapFilter[F, WebSocketFrame] {
        case WebSocketFrame.Text(payload, _, _) =>
          (decode[DataFrame](payload) match {
            case Left(ex) =>
              Logger[F].error(ex)("Failed to decode frame: " + payload) *> q.offer(None) // stopping
            case Right(decoded) =>
              q.offer(Some(decoded))
          }) *> F.pure(None)
        case _ =>
          q.offer(None).map(_ => None) // stopping
      }
    }

    Stream
      .resource {
        for {
          _     <- Logger[F].debug("ws connecting to: " + uri.toString()).toResource
          queue <- Queue.unbounded[F, Option[DataFrame]].toResource
          _ <- basicRequest
            .get(uri)
            .response(asWebSocketStreamAlways(Fs2Streams[F])(webSocketFramePipe(queue)))
            .send(client)
            .flatMap { response =>
              Logger[F].debug("response: " + response)
            }
            .background
        } yield queue
      }
      .flatMap(Stream.fromQueueNoneTerminated(_))
  }

  private def sendRequest[Response](
      request: RequestT[Identity, Response, Any],
      limiters: List[RateLimiter[F]],
      weight: Int
  ): F[Response] = {
    val processRequest = F.defer(for {
      _           <- Logger[F].debug(s"${request.method} ${request.uri}")
      rawResponse <- request.send(client)
    } yield rawResponse.body)
    limiters.foldLeft(processRequest) { case (response, limiter) =>
      limiter.await(response, weight = weight)
    }
  }
}

object HttpClient {
  def make[F[_]: Async: Logger](implicit
      client: SttpBackend[F, Any with Fs2Streams[F] with capabilities.WebSockets]
  ): F[HttpClient[F]] =
    new HttpClient[F]().pure[F]
}
