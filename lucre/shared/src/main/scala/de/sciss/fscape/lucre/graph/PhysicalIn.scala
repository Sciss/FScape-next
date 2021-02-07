/*
 *  WebAudioIn.scala
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
package lucre.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object PhysicalIn extends ProductReader[PhysicalIn] {
  override def read(in: RefMapIn, key: String, arity: Int): PhysicalIn = {
    require (arity == 2)
    val _indices      = in.readGE()
    val _numChannels  = in.readInt()
    new PhysicalIn(_indices, _numChannels)
  }
}
// XXX TODO: `indices` currently unused
final case class PhysicalIn(indices: GE = 0, numChannels: Int = 1) extends UGenSource.MultiOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = makeUGen(Vector.empty)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
      adjuncts = Adjunct.Int(numChannels) :: Nil,
      isIndividual = true, hasSideEffect = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] =
    lucre.stream.PhysicalIn(numChannels = numChannels)
}