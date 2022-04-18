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

package io.github.paoloboni.http

import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.client.WireMock._
import io.circe.Json
import io.github.paoloboni.Env.log
import io.github.paoloboni.binance.IntegrationTest
import io.github.paoloboni.binance.common.response.CirceResponse
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client3.circe._
import sttp.client3.{HttpError, SttpBackend, UriContext}

object HttpClientTest extends IntegrationTest {

  integrationTest("a bad request response should be translated into a HttpError") { server =>
    val responseBody = """{ "error": "bad request" }"""

    mkTestClient
      .use { implicit client =>
        for {
          _ <- IO.delay(server.stubFor(get("/test").willReturn(aResponse().withStatus(400).withBody(responseBody))))
          httpClient <- HttpClient.make[IO]
          url = uri"http://localhost:${server.port().toString}/test"
          responseOrError <- httpClient.get[CirceResponse[Json]](url, asJson[Json], limiters = List.empty)
          response        <- IO.fromEither(responseOrError)
        } yield response
      }
      .attempt
      .map { result =>
        val error = result.left.toOption.get.asInstanceOf[HttpError[String]]
        expect.all(
          error.statusCode.code == 400,
          error.body == responseBody
        )
      }
  }

  private val mkTestClient: Resource[IO, SttpBackend[IO, Any with Fs2Streams[IO] with capabilities.WebSockets]] =
    AsyncHttpClientFs2Backend.resourceUsingConfig[IO](
      new DefaultAsyncHttpClientConfig.Builder().setRequestTimeout(5000).build()
    )
}
