/*
 *  Frames.scala
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
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object Frames extends ProductReader[Frames] {
  override def read(in: RefMapIn, key: String, arity: Int): Frames = {
    require (arity == 1)
    val _in = in.readGE()
    new Frames(_in)
  }
}
/** A UGen that generates a signal that incrementally counts the frames of its input.
  * The first value output will be `1`, and the last will correspond to the number
  * of frames seen, i.e. `Length(in)`.
  */
final case class Frames(in: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    import in.tpe
    stream.Frames[in.A, in.Buf](in = in.toElem)
  }
}

object Indices extends ProductReader[Indices] {
  override def read(in: RefMapIn, key: String, arity: Int): Indices = {
    require (arity == 1)
    val _in = in.readGE()
    new Indices(_in)
  }
}
/** A UGen that generates a signal that incrementally counts the frames of its input.
  * The first value output will be `0`, and the last will correspond to the number
  * of frames seen minutes one.
  */
final case class Indices(in: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    import in.tpe
    stream.Frames[in.A, in.Buf](in = in.toElem, init = 0, name = name)
  }
}