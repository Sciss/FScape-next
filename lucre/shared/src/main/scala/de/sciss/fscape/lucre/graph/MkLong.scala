/*
 *  MkLong.scala
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

package de.sciss.fscape.lucre.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.lucre.{UGenGraphBuilder, stream => Lstream}
import de.sciss.fscape.stream.StreamIn
import de.sciss.fscape.{GE, Lazy, UGen, UGenGraph, UGenIn, UGenSource, stream}
import de.sciss.lucre.{LongObj, Obj, Txn, Workspace}
import de.sciss.serial.{DataInput, TFormat}
import de.sciss.proc.FScape.Output

import scala.collection.immutable.{IndexedSeq => Vec}

object MkLong extends ProductReader[MkLong] {
  final case class WithRef private(peer: MkLong, ref: OutputRef) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, Vector(peer.in.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, args, adjuncts = Adjunct.String(ref.key) :: Nil)
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val Vec(in) = args
      Lstream.MkLong(in = in.toLong, ref = ref)
    }

    override def productPrefix: String = s"MkLong$$WithRef"
  }

  override def read(in: RefMapIn, key: String, arity: Int): MkLong = {
    require (arity == 2)
    val _key  = in.readString()
    val _in   = in.readGE()
    new MkLong(_key, _in)
  }
}
final case class MkLong(key: String, in: GE) extends Lazy.Expander[Unit] with Output.Reader {

  def tpe: Obj.Type = LongObj

  override def readOutputValue(in: DataInput): Long =
    TFormat.Long.read(in)

  def readOutput[T <: Txn[T]](in: DataInput)(implicit tx: T, workspace: Workspace[T]): Obj[T] = {
    val flat = readOutputValue(in)
    LongObj.newConst(flat)
  }

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub      = UGenGraphBuilder.get(b)
    val refOpt  = ub.requestOutput(this)
    val ref     = refOpt.getOrElse(sys.error(s"Missing output $key"))
    MkLong.WithRef(this, ref)
    ()
  }
}