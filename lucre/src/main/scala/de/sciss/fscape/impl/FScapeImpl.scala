/*
 *  FScapeImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package impl

import de.sciss.lucre.event.Targets
import de.sciss.lucre.stm.impl.ObjSerializer
import de.sciss.lucre.stm.{Copy, Elem, NoSys, Obj, Sys}
import de.sciss.lucre.{event => evt}
import de.sciss.serial.{DataInput, DataOutput, Serializer}

import scala.collection.immutable.{IndexedSeq => Vec}

object FScapeImpl {
  private final val SER_VERSION = 0x4673  // "Fs"

  def apply[S <: Sys[S]](implicit tx: S#Tx): FScape[S] = new New[S]

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] =
    serializer[S].read(in, access)

  def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, FScape[S]] = anySer.asInstanceOf[Ser[S]]

  private val anySer = new Ser[NoSys]

  private class Ser[S <: Sys[S]] extends ObjSerializer[S, FScape[S]] {
    def tpe: Obj.Type = FScape
  }

  def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] = {
    val targets = Targets.read(in, access)
    new Read(in, access, targets)
  }

  private sealed trait Impl[S <: Sys[S]]
    extends FScape[S] with evt.impl.SingleNode[S, FScape.Update[S]] {
    proc =>

    final def tpe: Obj.Type = FScape

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets   = Targets[Out]
        val graph               = context(proc.graph)
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    final def connect()(implicit tx: S#Tx): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: S#Tx): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with evt.impl.Generator[S, FScape.Update[S]] {
      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[FScape.Update[S]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = Option.empty[FScape.Update[S]] // if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[S]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[S]]) { u =>
          Vector(GraphChange(u))
        }

        val seq3 = stateOpt.fold(seq0) { u =>
          if (seq0.isEmpty) u.changes else seq0 ++ u.changes
        }
        if (seq3.isEmpty) None else Some(FScape.Update(proc, seq3))
      }
    }

    final protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      graph.write(out)
    }

    final protected def disposeData()(implicit tx: S#Tx): Unit = {
      disconnect()
      graph.dispose()
    }

    override def toString: String = s"FScape$id"
  }

  private final class New[S <: Sys[S]](implicit tx0: S#Tx) extends Impl[S] {
    protected val targets   = evt.Targets[S](tx0)
    val graph               = GraphObj.newVar(GraphObj.empty)
    connect()(tx0)
  }

  private final class Read[S <: Sys[S]](in: DataInput, access: S#Acc, protected val targets: evt.Targets[S])
                                       (implicit tx0: S#Tx)
    extends Impl[S] {

    {
      val serVer = in.readShort()
      if (serVer != SER_VERSION) sys.error(s"Incompatible serialized (found $serVer, required $SER_VERSION)")
    }

    val graph = GraphObj.readVar(in, access)
  }
}