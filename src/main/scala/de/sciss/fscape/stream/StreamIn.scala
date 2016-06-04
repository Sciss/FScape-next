/*
 *  StreamIn.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

//object StreamIn {
//  implicit def fromDouble   (peer:     OutD ):     StreamIn  = ???
//  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamIn] = ???
//}
trait StreamIn {
  def toDouble(implicit b: Builder): OutD
  def toInt   (implicit b: Builder): OutI
}

object StreamOut {
  implicit def fromDouble   (peer:     OutD ):     StreamOut  = Double(peer)
  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamOut] = peer.map(Double)

  final case class Double(peer: OutD) extends StreamOut
}
sealed trait StreamOut