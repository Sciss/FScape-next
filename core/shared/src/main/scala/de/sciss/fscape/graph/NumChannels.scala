/*
 *  NumChannels.scala
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
import de.sciss.fscape.stream.{Builder, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object NumChannels extends ProductReader[NumChannels] {
  override def read(in: RefMapIn, key: String, arity: Int): NumChannels = {
    require (arity == 1)
    val _in = in.readGE()
    new NumChannels(_in)
  }
}
/** A graph element that produces an integer with number-of-channels of the input element.
  */
final case class NumChannels(in: GE) extends UGenSource.SingleOut  {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UGenSource.unwrap(this, in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    ConstantI(args.size)

  // XXX TODO --- is this ever called?
  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: Builder): StreamOut = {
    val Vec(sz) = args
    sz.toInt
  }
}
