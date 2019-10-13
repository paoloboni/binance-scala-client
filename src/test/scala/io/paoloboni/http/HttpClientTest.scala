/*
 * Copyright (c) 2019 Paolo Boni
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

package io.paoloboni.http

import cats.effect.IO
import com.github.tomakehurst.wiremock.client.WireMock._
import io.circe.Json
import io.lemonlabs.uri.Url
import io.paoloboni.Env
import io.paoloboni.integration._
import org.scalatest.{EitherValues, FreeSpec, Matchers}

class HttpClientTest extends FreeSpec with Matchers with EitherValues with Env {
  "a bad request response should be translated into a HttpError" in withWiremockServer { server =>
    val responseBody = """{ "error": "bad request" }"""
    server.stubFor(get("/test").willReturn(aResponse().withStatus(400).withBody(responseBody)))
    val httpClient = HttpClient[IO].unsafeRunSync()

    val url = Url.parse(s"http://localhost:${server.port().toString}/test")

    val result = httpClient.get[Json](url).attempt.unsafeRunSync()

    result.left.value shouldBe a[HttpError]

    val error = result.left.value.asInstanceOf[HttpError]
    error.status.code shouldBe 400
    error.body shouldBe responseBody
  }
}
