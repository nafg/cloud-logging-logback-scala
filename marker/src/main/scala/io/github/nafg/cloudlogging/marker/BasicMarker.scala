package io.github.nafg.cloudlogging.marker

import org.slf4j.Marker

import java.util
import scala.jdk.CollectionConverters._

// based on org.slf4j.helpers.BasicMarker
protected class BasicMarker(name: String, references: Seq[Marker])
    extends Marker {
  override def getName: String = name
  override def add(reference: Marker): Unit =
    throw new UnsupportedOperationException
  override def hasReferences: Boolean = references.nonEmpty
  //noinspection ScalaDeprecation
  override def hasChildren: Boolean = hasReferences
  override def iterator: util.Iterator[Marker] = references.iterator.asJava
  override def remove(referenceToRemove: Marker): Boolean =
    throw new UnsupportedOperationException
  override def contains(other: Marker): Boolean =
    this == other || references.exists(_.contains(other))
  override def contains(name: String): Boolean =
    this.name == name || references.exists(_.contains(name))
  override def equals(obj: Any): Boolean =
    if (obj == this)
      true
    else if (obj == null)
      false
    else
      obj match {
        case other: Marker => name == other.getName
        case _             => false
      }
  override def hashCode: Int = name.hashCode
  override def toString: String =
    getName +
      (if (!this.hasReferences) ""
       else " [ " + references.map(_.getName).mkString(", ") + " ]")
}
