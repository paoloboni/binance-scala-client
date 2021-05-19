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
import cats.syntax.all._
import io.github.paoloboni.http.ratelimit._
import io.lemonlabs.uri.Url
import log.effect.LogWriter
import org.http4s.client.Client
import org.http4s.{Status, _}
import org.typelevel.ci.CIString

sealed class HttpClient[F[_]: Async: Client: LogWriter] {

  def get[Response](
      url: Url,
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  )(implicit responseDecoder: EntityDecoder[F, Response]): F[Response] = for {
    uri <- Uri.fromString(url.toStringPunycode).pure[F].rethrow
    request = Request[F](
      method = Method.GET,
      uri = uri,
      headers = Headers(headers.map { case (name, value) =>
        Header.Raw(CIString(name), value)
      }.toList)
    )
    response <- sendRequest[Response](request, limiters, weight)
  } yield response

  def post[Request, Response](
      url: Url,
      requestBody: Request,
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  )(implicit
      requestEncoder: EntityEncoder[F, Request],
      responseDecoder: EntityDecoder[F, Response]
  ): F[Response] =
    for {
      uri <- Uri.fromString(url.toStringPunycode).pure[F].rethrow
      request = Request[F](
        method = Method.POST,
        uri = uri,
        headers = Headers(headers.map { case (name, value) =>
          Header.Raw(CIString(name), value)
        }.toList)
      ).withEntity(requestBody)
      response <- sendRequest[Response](request, limiters, weight)
    } yield response

  def delete[Request, Response](
      url: Url,
      requestBody: Request,
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  )(implicit
      encoder: Encoder[Request],
      decoder: Decoder[Response]
  ): F[Response] = {
    val request = Request[F](
      method = Method.DELETE,
      uri = Uri.unsafeFromString(url.toStringPunycode),
      headers = Headers(headers.map { case (name, value) =>
        Header.Raw(CIString(name), value)
      }.toList)
    ).withEntity(requestBody)

    sendRequest(request, limiters, weight)
  }

  def delete[Request, Response](
      url: Url,
      requestBody: Request,
      limiters: List[RateLimiter[F]],
      headers: Map[String, String] = Map.empty,
      weight: Int = 1
  )(implicit
      encoder: Encoder[Request],
      decoder: Decoder[Response]
  ): F[Response] = {
    val request = Request[F](
      method = Method.DELETE,
      uri = Uri.unsafeFromString(url.toStringPunycode),
      headers = Headers(headers.map { case (name, value) =>
        Header.Raw(CIString(name), value)
      }.toList)
    ).withEntity(requestBody)

    sendRequest(request, limiters, weight)
  }

  private def sendRequest[Response](
      request: Request[F],
      limiters: List[RateLimiter[F]],
      weight: Int
  )(implicit
      decoder: EntityDecoder[F, Response]
  ): F[Response] = {
    for {
      _ <- LogWriter.debug(s"${request.method} ${request.uri}")
      decoded <- {
        val httpRequest = implicitly[Client[F]]
          .expectOr(request) { error =>
            for {
              errorBody <- error.as[String]
              _         <- LogWriter.error(s"Failed response. Status=${error.status} Body='$errorBody'")
            } yield HttpError(error.status, errorBody)
          }

        limiters.foldLeft(httpRequest) { case (response, limiter) =>
          limiter.await(response, weight = weight)
        }
      }
    } yield decoded
  }
}

case class HttpError(status: Status, body: String) extends Exception

object HttpClient {
  def make[F[_]: Async: Client: LogWriter]: F[HttpClient[F]] =
    new HttpClient[F]().pure[F]
}
