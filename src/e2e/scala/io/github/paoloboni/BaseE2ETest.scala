package io.github.paoloboni

import cats.effect.IO
import io.github.paoloboni.binance.common.BinanceConfig
import weaver.IOSuite

abstract class BaseE2ETest[API] extends IOSuite {

  override type Res = API

  def config: BinanceConfig.Aux[IO, API]
}
