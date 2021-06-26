package io.github.paoloboni.binance.common.response

import io.circe.Decoder
import io.github.paoloboni.binance.common.response.DiffDepthStream.{Ask, Bid}

import scala.util.Try

final case class DiffDepthStream(
    e: String,   // Event type
    E: Long,     // Event time
    s: String,   // Symbol
    U: Long,     // First update ID in event
    u: Long,     // Final update ID in event
    b: Seq[Bid], // Bids to be updated
    a: Seq[Ask]  // Asks to be updated
)

object DiffDepthStream {
  final case class Bid(price: BigDecimal, quantity: BigDecimal)
  final case class Ask(price: BigDecimal, quantity: BigDecimal)

  object IsBigDecimal {
    def unapply(arg: String): Option[BigDecimal] = Try(BigDecimal(arg)).toOption
  }

  implicit val bidDecoder: Decoder[Bid] =
    Decoder.decodeList[String].emap {
      case List(IsBigDecimal(price), IsBigDecimal(quantity)) =>
        Right(Bid(price, quantity))
      case other => Left("Invalid Bid: " + other)
    }

  implicit val askDecoder: Decoder[Ask] =
    Decoder.decodeList[String].emap {
      case List(IsBigDecimal(price), IsBigDecimal(quantity)) =>
        Right(Ask(price, quantity))
      case other => Left("Invalid Ask: " + other)
    }

  import io.circe.generic.semiauto._
  implicit val decoder: Decoder[DiffDepthStream] = deriveDecoder[DiffDepthStream]
}
