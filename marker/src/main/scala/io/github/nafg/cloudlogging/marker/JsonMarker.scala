package io.github.nafg.cloudlogging.marker

import io.circe.{Encoder, Json}
import org.slf4j.Marker

import java.time.Duration

case class JsonMarker(
  name: String,
  data: Json,
  httpRequest: Option[JsonMarker.HttpRequest] = None,
  labels: Map[String, String] = Map.empty,
  operation: Option[JsonMarker.Operation] = None,
  sourceLocation: Option[JsonMarker.SourceLocation] = None,
  spanId: Option[String] = None,
  trace: Option[String] = None,
  traceSampled: Option[Boolean] = None,
  references: Seq[Marker] = Nil
) extends BasicMarker(name, references)
object JsonMarker {
  def encode[A: Encoder](name: String, references: Marker*)(data: A)(implicit
    httpRequest: HttpRequest = null,
    operation: Operation = null,
    sourceLocation: SourceLocation = null
  ): JsonMarker =
    new JsonMarker(
      name = name,
      data = Encoder[A].apply(data),
      httpRequest = Option(httpRequest),
      operation = Option(operation),
      sourceLocation = Option(sourceLocation),
      references = references
    )

  case class Operation(id: String, producer: String, first: Boolean = false, last: Boolean = false)

  case class SourceLocation(file: Option[String] = None, line: Option[Long] = None, function: Option[String] = None)

  case class HttpRequest(
    requestMethod: String,
    requestUrl: String,
    requestSize: Option[Long] = None,
    status: Option[Int] = None,
    responseSize: Option[Long] = None,
    userAgent: Option[String] = None,
    remoteIp: String,
    serverIp: Option[String] = None,
    referer: Option[String] = None,
    latency: Option[Duration] = None,
    cacheLookup: Option[Boolean] = None,
    cacheHit: Option[Boolean] = None,
    cacheValidatedWithOriginServer: Option[Boolean] = None,
    cacheFillBytes: Option[Long] = None
  )
}
