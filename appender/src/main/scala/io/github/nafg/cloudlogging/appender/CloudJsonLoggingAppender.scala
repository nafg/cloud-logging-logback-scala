package io.github.nafg.cloudlogging.appender

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, IThrowableProxy, ThrowableProxy}
import com.google.cloud.logging.HttpRequest.RequestMethod
import com.google.cloud.logging.Logging.WriteOption
import com.google.cloud.logging.logback.LoggingAppender
import com.google.cloud.logging.{Option as _, *}
import io.circe.{Json, JsonNumber, JsonObject}
import io.github.nafg.cloudlogging.marker.JsonMarker
import org.slf4j.Marker
import org.threeten.bp.Duration

import java.io.{PrintWriter, StringWriter}
import java.time.Instant
import java.util
import java.util.Collections
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object CloudJsonLoggingAppender {
  private def severityFor(level: Level) =
    level match {
      case Level.TRACE | Level.DEBUG => Severity.DEBUG
      case Level.INFO                => Severity.INFO
      case Level.WARN                => Severity.WARNING
      case Level.ERROR               => Severity.ERROR
      case _                         => Severity.DEFAULT
    }

  private def someMap[A, B](entries: (A, Option[B])*): Map[A, B] =
    entries.collect { case (k, Some(v)) => (k, v) }.toMap

  private def stack(
    nullOrThrowProxy: IThrowableProxy,
    refs: mutable.Set[IThrowableProxy]
  ): Option[util.Map[String, AnyRef]] =
    Option(nullOrThrowProxy).map { throwProxy =>
      if (refs.contains(throwProxy))
        someMap("circularRef" -> Some(throwProxy.toString: AnyRef)).asJava
      else {
        refs += throwProxy
        someMap[String, AnyRef](
          "className"  -> Some(throwProxy.getClassName),
          "message"    -> Some(throwProxy.getMessage),
          "stack"      -> Option(throwProxy.getStackTraceElementProxyArray)
            .map(_.map(_.getStackTraceElement.toString)),
          "suppressed" -> Some(throwProxy.getSuppressed.flatMap(stack(_, refs))).filter(_.nonEmpty),
          "cause"      -> stack(throwProxy.getCause, refs)
        ).asJava
      }
    }

  private object JsonToRaw extends Json.Folder[Any] {
    override def onNull: Null                                       = null
    override def onBoolean(value: Boolean): Boolean                 = value
    override def onNumber(value: JsonNumber): Double                = value.toDouble
    override def onString(value: String): String                    = value
    override def onArray(value: Vector[Json]): util.List[Any]       =
      value.map(_.foldWith[Any](JsonToRaw)).asJava
    override def onObject(value: JsonObject): util.Map[String, Any] =
      value.toMap.map { case (k, v) => (k, v.foldWith[Any](JsonToRaw)) }.asJava
  }

  def marker: Marker => Any = {
    case jsonMarker: JsonMarker =>
      Map(
        "name"       -> jsonMarker.name,
        "data"       -> jsonMarker.data.foldWith[Any](JsonToRaw),
        "references" -> jsonMarker.references.map(marker).toArray
      ).asJava
    case m if m.hasReferences   =>
      Map("name" -> m.getName, "references" -> m.iterator.asScala.map(marker).toArray).asJava
    case m                      =>
      m.getName
  }

  private val legalNameChars            =
    ('a' to 'z').toSet ++ ('A' to 'Z') ++ ('0' to '9') ++ Set('/', '_', '-', '.')
  private def legalizeNameChar(c: Char) = if (legalNameChars(c)) c else '-'

  def logEntryFor(e: ILoggingEvent): LogEntry = {
    val level = e.getLevel

    val message =
      e.getFormattedMessage +
        Option(e.getThrowableProxy).fold("") { iThrowableProxy =>
          def render(throwable: Throwable) = {
            val writer = new StringWriter()
            writer.append('\n')
            throwable.printStackTrace(new PrintWriter(writer))
            writer.toString
          }

          iThrowableProxy match {
            case throwableProxy: ThrowableProxy =>
              render(throwableProxy.getThrowable)
            case _                              =>
              val refs =
                new util.IdentityHashMap[IThrowableProxy, Throwable].asScala

              def toThrowable(proxy: IThrowableProxy): Throwable =
                refs.getOrElseUpdate(
                  proxy, {
                    val res =
                      new Throwable(proxy.getMessage, toThrowable(proxy.getCause))
                    proxy.getSuppressed.foreach(s => res.addSuppressed(toThrowable(s)))
                    res
                  }
                )

              render(toThrowable(iThrowableProxy))
          }
        }

    def toScala[A](list: util.List[A]) = if (list eq null) Nil else list.asScala.toList

    val markers = toScala(e.getMarkerList)

    val payload =
      someMap(
        "message"   -> Some(message),
        "markers"   -> Some(markers.map(marker).asJava).filterNot(_.isEmpty),
        "throwable" -> stack(
          e.getThrowableProxy,
          Collections
            .newSetFromMap[IThrowableProxy](new util.IdentityHashMap)
            .asScala
        ),
        "values"    ->
          Some(toScala(e.getKeyValuePairs).map(kv => (kv.key, kv.value)).toMap.asJava)
            .filterNot(_.isEmpty)
      )

    val jsonMarkers = markers.collect { case jsonMarker: JsonMarker =>
      jsonMarker
    }

    val labels =
      Map(
        "levelName"  -> level.toString,
        "levelValue" -> String.valueOf(level.toInt),
        "threadName" -> e.getThreadName
      ) ++
        e.getMDCPropertyMap.asScala ++
        jsonMarkers.flatMap(_.labels).toMap

    val builder =
      LogEntry
        .newBuilder(Payload.JsonPayload.of(payload.asJava))
        .setLogName(e.getLoggerName.map(legalizeNameChar))
        .setTimestamp(Instant.ofEpochMilli(e.getTimeStamp))
        .setSeverity(severityFor(level))
        .setLabels(labels.asJava)

    jsonMarkers.foreach { jsonMarker =>
      jsonMarker.httpRequest.foreach { httpRequest =>
        val httpRequestBuilder =
          HttpRequest
            .newBuilder()
            .setRequestMethod(RequestMethod.valueOf(httpRequest.requestMethod))
            .setRequestUrl(httpRequest.requestUrl)
            .setRemoteIp(httpRequest.remoteIp)

        httpRequest.userAgent.foreach(httpRequestBuilder.setUserAgent)
        httpRequest.referer.foreach(httpRequestBuilder.setReferer)
        httpRequest.latency.foreach(d => httpRequestBuilder.setLatency(Duration.parse(d.toString)))
        // noinspection DuplicatedCode
        httpRequest.cacheLookup.foreach(httpRequestBuilder.setCacheLookup)
        httpRequest.cacheHit.foreach(httpRequestBuilder.setCacheHit)
        httpRequest.cacheValidatedWithOriginServer.foreach(httpRequestBuilder.setCacheValidatedWithOriginServer)
        httpRequest.cacheFillBytes.foreach(httpRequestBuilder.setCacheFillBytes)

        builder.setHttpRequest(httpRequestBuilder.build())
      }

      jsonMarker.operation.foreach { operation =>
        builder.setOperation(
          Operation
            .newBuilder(operation.id, operation.producer)
            .setFirst(operation.first)
            .setLast(operation.last)
            .build()
        )
      }

      jsonMarker.sourceLocation.foreach { sourceLocation =>
        val sourceLocationBuilder =
          SourceLocation
            .newBuilder()

        sourceLocation.file.foreach(sourceLocationBuilder.setFile)
        sourceLocation.line.foreach(l => sourceLocationBuilder.setLine(l))
        sourceLocation.function.foreach(sourceLocationBuilder.setFunction)

        builder.setSourceLocation(sourceLocationBuilder.build())
      }

      // noinspection DuplicatedCode
      jsonMarker.spanId.foreach(builder.setSpanId)
      jsonMarker.trace.foreach(builder.setTrace)
      jsonMarker.traceSampled.foreach(builder.setTraceSampled)
    }

    builder.build
  }

}

class CloudJsonLoggingAppender extends LoggingAppender {
  private lazy val logging  = LoggingOptions.getDefaultInstance.getService
  private lazy val resource = MonitoredResourceUtil.getResource(LoggingOptions.getDefaultInstance.getProjectId, null)
  private lazy val defaultWriteOptions =
    Seq(WriteOption.logName("java.log"), WriteOption.resource(resource))

  override def start(): Unit = if (!isStarted) {
    logging.setFlushSeverity(CloudJsonLoggingAppender.severityFor(Level.ERROR))
    super.start()
  }

  private def writeLogEntry(logEntry: LogEntry): Unit =
    logging.write(Collections.singleton(logEntry), defaultWriteOptions*)

  override def append(e: ILoggingEvent): Unit =
    try {
      writeLogEntry(CloudJsonLoggingAppender.logEntryFor(e))
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        writeLogEntry(
          LogEntry
            .newBuilder(Payload.StringPayload.of(e.toString))
            .setSeverity(Severity.ERROR)
            .setLogName(getClass.getName.map(CloudJsonLoggingAppender.legalizeNameChar))
            .build()
        )
    }
}
