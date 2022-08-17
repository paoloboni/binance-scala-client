package io.github.paoloboni

import cats.effect.IO
import io.github.paoloboni.binance.common.BinanceConfig
import weaver.IOSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

abstract class BaseE2ETest[API] extends IOSuite {

  override type Res = API

  def config: BinanceConfig

  implicit class RetryOps[A](ioa: IO[A]) {
    def retryWithBackoff(initialDelay: FiniteDuration = 100.millis, maxRetries: Int = 3): IO[A] = {
      ioa.handleErrorWith { error =>
        if (maxRetries > 0)
          IO.sleep(initialDelay) *> retryWithBackoff(initialDelay * 2, maxRetries - 1)
        else
          IO.raiseError(error)
      }
    }
  }
}
