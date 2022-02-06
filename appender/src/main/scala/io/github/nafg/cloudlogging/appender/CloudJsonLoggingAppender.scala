package io.github.nafg.cloudlogging.appender

import java.io.{PrintWriter, StringWriter}
import java.util
import java.util.Collections

import scala.jdk.CollectionConverters._
import scala.collection.mutable

import io.github.nafg.cloudlogging.marker.JsonMarker

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, IThrowableProxy, ThrowableProxy}
import com.google.cloud.logging.Logging.WriteOption
import com.google.cloud.logging.logback.LoggingAppender
import com.google.cloud.logging.{Option => _, _}
import io.circe.{Json, JsonNumber, JsonObject}
import org.slf4j.Marker


object CloudJsonLoggingAppender {
  def severityFor(level: Level) =
    level match {
      case Level.TRACE | Level.DEBUG => Severity.DEBUG
      case Level.INFO                => Severity.INFO
      case Level.WARN                => Severity.WARNING
      case Level.ERROR               => Severity.ERROR
      case _                         => Severity.DEFAULT
    }

  def someMap[A, B](entries: (A, Option[B])*): Map[A, B] =
    entries.collect { case (k, Some(v)) => (k, v) }.toMap

  def stack(
             nullOrThrowProxy: IThrowableProxy,
             refs: mutable.Set[IThrowableProxy]
           ): Option[util.Map[String, AnyRef]] =
    Option(nullOrThrowProxy).map { throwProxy =>
      if (refs.contains(throwProxy))
        someMap("circularRef" -> Some(throwProxy.toString: AnyRef)).asJava
      else {
        refs += throwProxy
        someMap(
          "className" -> Some(throwProxy.getClassName),
          "message" -> Some(throwProxy.getMessage),
          "stack" -> Option(throwProxy.getStackTraceElementProxyArray)
            .map(_.map(_.getStackTraceElement.toString)),
          "suppressed" -> Some(throwProxy.getSuppressed.map(stack(_, refs)))
            .filter(_.nonEmpty),
          "cause" -> stack(throwProxy.getCause, refs)
        ).asJava
      }
    }

  object JsonToRaw extends Json.Folder[Any] {
    override def onNull = null
    override def onBoolean(value: Boolean) = value
    override def onNumber(value: JsonNumber) = value.toDouble
    override def onString(value: String) = value
    override def onArray(value: Vector[Json]) =
      value.map(_.foldWith[Any](JsonToRaw)).asJava
    override def onObject(value: JsonObject) =
      value.toMap.map { case (k, v) => (k, v.foldWith[Any](JsonToRaw)) }.asJava
  }

  def marker: Marker => Any = {
    case JsonMarker(_name, data, references@_*) =>
      Map(
        "name" -> _name,
        "data" -> data.foldWith[Any](JsonToRaw),
        "references" -> references.map(marker).toArray
      ).asJava
    case m if m.hasReferences                   =>
      Map(
        "name" -> m.getName,
        "references" -> m.iterator.asScala.map(marker).toArray
      ).asJava
    case m                                      =>
      m.getName
  }

  val legalNameChars = ('a' to 'z').toSet ++ ('A' to 'Z') ++ ('0' to '9') ++ Set('/', '_', '-', '.')
  def legalizeNameChar(c: Char) = if (legalNameChars(c)) c else '-'

  def logEntryFor(e: ILoggingEvent) = {
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
                refs.getOrElseUpdate(proxy, {
                  val res =
                    new Throwable(proxy.getMessage, toThrowable(proxy.getCause))
                  proxy.getSuppressed.foreach(
                    s => res.addSuppressed(toThrowable(s))
                  )
                  res
                })

              render(toThrowable(iThrowableProxy))
          }
        }

    val payload =
      someMap(
        "message" -> Some(message),
        "marker" -> Option(e.getMarker).map(marker),
        "throwable" -> stack(
          e.getThrowableProxy,
          Collections
            .newSetFromMap[IThrowableProxy](new util.IdentityHashMap)
            .asScala
        )
      )
    val labels =
      Map(
        "levelName" -> level.toString,
        "levelValue" -> String.valueOf(level.toInt),
        "threadName" -> e.getThreadName
      ) ++
        e.getMDCPropertyMap.asScala

    LogEntry
      .newBuilder(Payload.JsonPayload.of(payload.asJava))
      .setLogName(e.getLoggerName.map(legalizeNameChar))
      .setTimestamp(e.getTimeStamp)
      .setSeverity(severityFor(level))
      .setLabels(labels.asJava)
      .build
  }

}

class CloudJsonLoggingAppender extends LoggingAppender {
  private lazy val logging = LoggingOptions.getDefaultInstance.getService
  private lazy val resource = MonitoredResourceUtil.getResource(
    LoggingOptions.getDefaultInstance.getProjectId,
    null
  )
  private lazy val defaultWriteOptions =
    Seq(WriteOption.logName("java.log"), WriteOption.resource(resource))

  override def start(): Unit = if (!isStarted) {
    logging.setFlushSeverity(CloudJsonLoggingAppender.severityFor(Level.ERROR))
    started = true
  }

  override def append(e: ILoggingEvent): Unit = {
    val logEntry = CloudJsonLoggingAppender.logEntryFor(e)
    logging.write(Collections.singleton(logEntry), defaultWriteOptions: _*)
  }
}
