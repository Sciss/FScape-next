/*
 *  OutputImpl.scala
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
package impl

import de.sciss.fscape.lucre.FScape.Output
import de.sciss.lucre.stm.impl.ObjSerializer
import de.sciss.lucre.stm.{Copy, Elem, NoSys, Obj, Sys}
import de.sciss.lucre.{event => evt}
import de.sciss.serial.{DataInput, DataOutput, Serializer}

object OutputImpl {
  private final val SER_VERSION = 0x464F  // "FO"

  sealed trait Update[S]

  def apply[S <: Sys[S]](fscape: FScape[S], key: String, tpe: Obj.Type)(implicit tx: S#Tx): OutputImpl[S] = {
    val tgt = evt.Targets[S]
//    val id  = tx.newID()
    val vr  = tx.newVar[Option[Obj[S]]](tgt.id, None)
    new Impl[S](tgt, fscape, key, tpe, vr).connect()
  }

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Output[S] =
    serializer[S].read(in, access)

  def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, Output[S]] = anySer.asInstanceOf[Ser[S]]

  implicit def implSer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, OutputImpl[S]] =
    anySer.asInstanceOf[Serializer[S#Tx, S#Acc, OutputImpl[S]]]

  private val anySer = new Ser[NoSys]

  private final class Ser[S <: Sys[S]] extends ObjSerializer[S, Output[S]] {
    def tpe: Obj.Type = Output
  }

  def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Output[S] = {
    val tgt     = evt.Targets.read(in, access)
//    val cookie  = in.readByte()
//    if (cookie != 3) sys.error(s"Unexpected cookie, expected 3 found $cookie")
//    val id      = tx.readID(in, access)
    val serVer  = in.readShort()
    if (serVer != SER_VERSION)
      sys.error(s"Incompatible serialized version (found ${serVer.toInt.toHexString}, required ${SER_VERSION.toHexString})")

    val fscape  = FScape.read(in, access)
    val key     = in.readUTF()
    val tpeID   = in.readInt()
    val tpe     = Obj.getType(tpeID)
    val vr      = tx.readVar[Option[Obj[S]]](tgt.id, in)
    new Impl[S](tgt, fscape, key, tpe, vr)
  }

  private final class Impl[S <: Sys[S]](protected val targets: evt.Targets[S],
                                        val fscape: FScape[S], val key: String, val valueType: Obj.Type,
                                        valueVr: S#Var[Option[Obj[S]]])
    extends OutputImpl[S] with evt.impl.SingleNode[S, Output.Update[S]] {

    def tpe: Obj.Type = Output

    override def toString: String = s"Output($id, $fscape, $key, $valueType)"

    def value(implicit tx: S#Tx): Option[Obj[S]] = ???

    def value_=(v: Option[Obj[S]])(implicit tx: S#Tx): Unit = ???

    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    def connect()(implicit tx: S#Tx): this.type = {
      ??? // graph.changed ---> changed
      this
    }

    def disconnect()(implicit tx: S#Tx): Unit = {
      ??? // graph.changed -/-> changed
    }

    object changed extends Changed
      with evt.impl.Generator[S, Output.Update[S]] {

      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[Output.Update[S]] = {
        ???
      }
    }

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] = {
      val tgtOut  = evt.Targets[Out] // txOut.newID()
      val fscOut  = context(fscape)
      val vrOut   = txOut.newVar[Option[Obj[Out]]](tgtOut.id, None)  // correct to drop cache?
      val out     = new Impl[Out](tgtOut, fscOut, key, valueType, vrOut)
      out.connect()
    }

    protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      fscape.write(out)
      out.writeUTF(key)
      out.writeInt(valueType.typeID)
      valueVr.write(out)
    }

    protected def disposeData()(implicit tx: S#Tx): Unit =
      valueVr.dispose()
  }
}
sealed trait OutputImpl[S <: Sys[S]] extends Output[S] {
  def value_=(v: Option[Obj[S]])(implicit tx: S#Tx): Unit
}