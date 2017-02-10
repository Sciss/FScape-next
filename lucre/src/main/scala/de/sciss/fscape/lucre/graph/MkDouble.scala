/*
 *  MkDouble.scala
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

import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream
import de.sciss.fscape.stream.StreamIn
import de.sciss.lucre.expr.DoubleObj
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.serial.{DataInput, ImmutableSerializer}
import de.sciss.synth.proc.WorkspaceHandle

import scala.collection.immutable.{IndexedSeq => Vec}

object MkDouble {
  final case class WithRef(peer: MkDouble, ref: OutputRef) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, Vector(peer.in.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, aux = Aux.String(ref.key) :: Nil)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val Vec(in) = args
      lucre.stream.MkDouble(in = in.toDouble, ref = ref)
    }

    override def productPrefix: String = s"MkDouble$$WithRef"
  }
}
final case class MkDouble(key: String, in: GE) extends Lazy.Expander[Unit] with Output.Reader {

  def tpe: Obj.Type = DoubleObj

  def readOutput[S <: Sys[S]](in: DataInput)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Obj[S] = {
    val flat = ImmutableSerializer.Double.read(in)
    DoubleObj.newConst(flat)
  }

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub      = UGenGraphBuilder.get(b)
    val refOpt  = ub.requestOutput(this)
    val ref     = refOpt.getOrElse(sys.error(s"Missing output $key"))
    MkDouble.WithRef(this, ref)
  }
}