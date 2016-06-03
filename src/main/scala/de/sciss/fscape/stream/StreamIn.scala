package de.sciss.fscape
package stream

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object StreamIn {
  implicit def fromDouble   (peer:     OutD ):     StreamIn  = ???
  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamIn] = ???
}
trait StreamIn {
  def toDouble: OutD
  def toInt   : OutI
}
