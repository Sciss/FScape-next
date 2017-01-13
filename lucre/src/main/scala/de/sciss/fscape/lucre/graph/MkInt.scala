/*
 *  MkInt.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package graph

import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream
import de.sciss.fscape.stream.StreamIn
import de.sciss.lucre.expr.IntObj

import scala.collection.immutable.{IndexedSeq => Vec}

object MkInt {
  final case class WithRef(peer: MkInt, ref: OutputRef) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(Vector(peer.in.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, rest = ref)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val Vec(in) = args
      lucre.stream.MkInt(in = in.toInt, ref = ref)
    }

    override def productPrefix: String = classOf[WithRef].getName
  }
}
final case class MkInt(key: String, in: GE) extends Lazy.Expander[Unit] {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub      = UGenGraphBuilder.get(b)
    val refOpt  = ub.requestOutput(key, IntObj)
    val ref     = refOpt.getOrElse(sys.error(s"Missing output $key"))
    MkInt.WithRef(this, ref)
  }
}