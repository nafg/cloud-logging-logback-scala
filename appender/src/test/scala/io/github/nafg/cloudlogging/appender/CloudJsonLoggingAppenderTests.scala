package io.github.nafg.cloudlogging.appender

import ch.qos.logback.classic.spi.*
import ch.qos.logback.classic.{Level, LoggerContext}
import com.google.cloud.logging.{Payload, Severity}
import io.circe.Json
import io.github.nafg.cloudlogging.marker.JsonMarker
import org.scalatest.Inside.*
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.Marker
import org.slf4j.event.KeyValuePair

import java.util
import scala.jdk.CollectionConverters.*

class CloudJsonLoggingAppenderTests extends AnyFunSuite {
  test("Exception") {
    val loggingEvent: ILoggingEvent = new ILoggingEvent {
      override val getThreadName: String                       = Thread.currentThread().getName
      override def getLevel: Level                             = Level.ERROR
      override def getMessage: String                          = "This is a test message"
      override def getArgumentArray: Array[AnyRef]             = Array.empty
      override def getFormattedMessage: String                 = getMessage
      override def getLoggerName: String                       = getClass.getName
      override def getLoggerContextVO: LoggerContextVO         =
        new LoggerContextVO(new LoggerContext)
      override def getThrowableProxy: IThrowableProxy          =
        new ThrowableProxy(new RuntimeException("Some error"))
      override def getCallerData: Array[StackTraceElement]     = Array.empty
      override def hasCallerData: Boolean                      = false
      override def getMarkerList: util.List[Marker]            =
        List[Marker](JsonMarker("testMarker", Json.obj("markerKey" -> Json.fromString("markerValue")))).asJava
      override def getMDCPropertyMap: util.Map[String, String] =
        Map.empty[String, String].asJava
      override def getMdc: util.Map[String, String]            = getMDCPropertyMap
      override val getTimeStamp: Long                          = System.currentTimeMillis()
      override def getNanoseconds: Int                         = 0
      override def prepareForDeferredProcessing(): Unit        = ()
      override def getSequenceNumber: Long                     = 0
      override def getKeyValuePairs: util.List[KeyValuePair]   = List(new KeyValuePair("pairKey", "pairValue")).asJava
    }

    val logEntry = CloudJsonLoggingAppender.logEntryFor(loggingEvent)

    assert(logEntry.getSeverity == Severity.ERROR)

    val data = logEntry.getPayload[Payload.JsonPayload].getDataAsMap.asScala

    inside(data.get("message")) { case Some(message: String) =>
      assert(message.startsWith(loggingEvent.getMessage))
    }

    inside(data.get("markers")) { case Some(markers: util.List[util.Map[String, String]]) =>
      assert(markers.get(0).get("name") == "testMarker")
    }

    inside(data.get("throwable")) { case Some(throwable: util.Map[k, v]) =>
      assert(throwable.get("message") == "Some error")
      assert(throwable.containsKey("stack"))
    }
  }
}
