/*
 *  MkLong.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
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
import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm.{Obj, Sys, WorkspaceHandle}
import de.sciss.serial.{DataInput, Serializer}

import scala.collection.immutable.{IndexedSeq => Vec}

object MkLong {
  final case class WithRef(peer: MkLong, ref: OutputRef) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, Vector(peer.in.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, aux = Aux.String(ref.key) :: Nil)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val Vec(in) = args
      lucre.stream.MkLong(in = in.toLong, ref = ref)
    }

    override def productPrefix: String = s"MkLong$$WithRef"
  }
}
final case class MkLong(key: String, in: GE) extends Lazy.Expander[Unit] with Output.Reader {

  def tpe: Obj.Type = LongObj

  def readOutput[S <: Sys[S]](in: DataInput)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Obj[S] = {
    val flat = Serializer.Long.read(in)
    LongObj.newConst(flat)
  }

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub      = UGenGraphBuilder.get(b)
    val refOpt  = ub.requestOutput(this)
    val ref     = refOpt.getOrElse(sys.error(s"Missing output $key"))
    MkLong.WithRef(this, ref)
  }
}