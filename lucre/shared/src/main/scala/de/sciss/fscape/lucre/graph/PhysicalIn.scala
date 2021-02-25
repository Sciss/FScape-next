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
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.proc.AuralSystem

import scala.collection.immutable.{IndexedSeq => Vec}

object PhysicalIn extends ProductReader[PhysicalIn] {
  override def read(in: RefMapIn, key: String, arity: Int): PhysicalIn = {
    require (arity == 2)
    val _indices      = in.readGE()
    val _numChannels  = in.readInt()
    new PhysicalIn(_indices, _numChannels)
  }

  private final case class WithRef private(peer: PhysicalIn, auralSystem: AuralSystem)
    extends UGenSource.MultiOut {

    import peer.numChannels

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
      UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
        adjuncts = Adjunct.Int(numChannels) :: Nil,
        isIndividual = true, hasSideEffect = true)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] =
      lucre.stream.PhysicalIn(index = null, numChannels = numChannels, auralSystem = auralSystem)
  }
}
// XXX TODO: `indices` currently unused
final case class PhysicalIn(indices: GE = 0, numChannels: Int = 1) extends GE.Lazy {
  override protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val ub = UGenGraphBuilder.get(b)
    PhysicalIn.WithRef(this, ub.auralSystem)
  }
}