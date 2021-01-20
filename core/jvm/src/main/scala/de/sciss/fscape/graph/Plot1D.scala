/*
 *  Plot1D.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object Plot1D extends ProductReader[Plot1D] {
  override def read(in: RefMapIn, key: String, arity: Int): Plot1D = {
    require (arity == 3)
    val _in     = in.readGE()
    val _size   = in.readGE()
    val _label  = in.readString()
    new Plot1D(_in, _size, _label)
  }
}
/** Debugging utility that plots 1D "windows" of the input data.
  */
final case class Plot1D(in: GE, size: GE, label: String = "plot") extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
    UGen.ZeroOut(this, inputs = args, adjuncts = Adjunct.String(label) :: Nil)
    ()
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in, size) = args
    import in.tpe
    stream.Plot1D[in.A, in.Buf](in = in.toElem, size = size.toInt, label = label)
  }
}
