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
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object PhysicalOut extends ProductReader[PhysicalOut] {
  override def read(in: RefMapIn, key: String, arity: Int): PhysicalOut = {
    require (arity == 2)
    val _indices  = in.readGE()
    val _in       = in.readGE()
    new PhysicalOut(_indices, _in)
  }

  def apply(in: GE): PhysicalOut = new PhysicalOut(0, in)
}
// XXX TODO: `indices` currently unused
final case class PhysicalOut(indices: GE, in: GE) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = unwrap(this, in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
    UGen.ZeroOut(this, inputs = args, isIndividual = true)
    ()
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit =
    stream.WebAudioOut(in = args.map(_.toDouble))
}