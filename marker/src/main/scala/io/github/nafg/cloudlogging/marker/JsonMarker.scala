package io.github.nafg.cloudlogging.marker

import io.circe.{Encoder, Json}
import org.slf4j.Marker

case class JsonMarker(name: String, data: Json, references: Marker*)
    extends BasicMarker(name, references)
object JsonMarker {
  def apply[A: Encoder](name: String,
                        references: Marker*)(data: A): JsonMarker =
    JsonMarker(name, Encoder[A].apply(data), references: _*)
}
