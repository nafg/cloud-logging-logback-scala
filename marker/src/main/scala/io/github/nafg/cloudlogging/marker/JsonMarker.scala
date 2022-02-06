package io.github.nafg.cloudlogging.marker

import scala.jdk.CollectionConverters._

import io.circe.Json
import org.slf4j.Marker


case class JsonMarker(name: String, data: Json, references: Marker*) extends Marker {
  override def getName = name
  override def add(reference: Marker): Unit = throw new NotImplementedError
  override def remove(reference: Marker) = throw new NotImplementedError
  override def hasChildren = hasReferences
  override def hasReferences = references.nonEmpty
  override def iterator() = references.asJava.iterator()
  override def contains(other: Marker) =
    this == other || references.exists(_.contains(other))
  override def contains(name: String) =
    this.name == name || references.exists(_.contains(name))
}
