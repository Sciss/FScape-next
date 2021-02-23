/*
 *  WebAudioOut.scala
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
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.stream.{SinkIgnore, StreamIn}
import de.sciss.proc.AuralSystem

import scala.collection.immutable.{IndexedSeq => Vec}

object PhysicalOut extends ProductReader[PhysicalOut] {
  override def read(in: RefMapIn, key: String, arity: Int): PhysicalOut = {
    require (arity == 2)
    val _indices  = in.readGE()
    val _in       = in.readGE()
    new PhysicalOut(_indices, _in)
  }

  def apply(in: GE): PhysicalOut = new PhysicalOut(0, in)

  private final case class WithRef private(peer: PhysicalOut, auralSystem: AuralSystem)
    extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, peer.in.expand.outputs)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, inputs = args, isIndividual = true)
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val in = args
      lucre.stream.PhysicalOut(index = null /*indices.toInt*/, in = in.map(_.toDouble), auralSystem = auralSystem)
    }
  }
}
// XXX TODO: `indices` currently unused
final case class PhysicalOut(indices: GE, in: GE) extends Lazy.Expander[Unit] {
  override protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub = UGenGraphBuilder.get(b)
    PhysicalOut.WithRef(this, ub.auralSystem)
  }
}