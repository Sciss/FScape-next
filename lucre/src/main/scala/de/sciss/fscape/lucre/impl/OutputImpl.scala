/*
 *  OutputImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.FScape.Output
import de.sciss.lucre.event.impl.ConstObjImpl
import de.sciss.lucre.stm.impl.ObjSerializer
import de.sciss.lucre.stm.{Copy, Elem, NoSys, Obj, Sys}
import de.sciss.serial.{DataInput, DataOutput, Serializer}

object OutputImpl {
  private final val SER_VERSION = 0x464F  // "FO"

  sealed trait Update[S]

  def apply[S <: Sys[S]](fscape: FScape[S], key: String, tpe: Obj.Type)(implicit tx: S#Tx): OutputImpl[S] = {
//    val tgt = evt.Targets[S]
//    val id = tgt.id
    val id  = tx.newId()
    val vr  = tx.newVar[Option[Obj[S]]](id, None)
    new Impl[S](id, fscape, key, tpe, vr).connect()
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
    val constCookie = in.readByte()
    if (constCookie != 3)
      sys.error(s"Unexpected cookie (found $constCookie, expected 3)")
//    val tgt     = evt.Targets.read(in, access)
//    val id = tgt.id
    val id = tx.readId(in, access)
//    val cookie  = in.readByte()
//    if (cookie != 3) sys.error(s"Unexpected cookie, expected 3 found $cookie")
//    val id      = tx.readId(in, access)
    val serVer  = in.readShort()
    if (serVer != SER_VERSION)
      sys.error(s"Incompatible serialized version (found ${serVer.toInt.toHexString}, required ${SER_VERSION.toHexString})")

    val fscape  = FScape.read(in, access)
    val key     = in.readUTF()
    val tpeId   = in.readInt()
    val tpe     = Obj.getType(tpeId)
    val vr      = tx.readVar[Option[Obj[S]]](id, in)
    new Impl[S](id /* tgt */, fscape, key, tpe, vr)
  }

  private final class Impl[S <: Sys[S]](val id: S#Id /* protected val targets: evt.Targets[S] */,
                                        val fscape: FScape[S], val key: String, val valueType: Obj.Type,
                                        valueVr: S#Var[Option[Obj[S]]])
    extends OutputImpl[S] with ConstObjImpl[S, Any] /* evt.impl.SingleNode[S, Output.Update[S]] */ { self =>

    def tpe: Obj.Type = Output

    override def toString: String = s"Output($id, $fscape, $key, $valueType)"

    def value(implicit tx: S#Tx): Option[Obj[S]] = valueVr()

    def value_=(v: Option[Obj[S]])(implicit tx: S#Tx): Unit = {
      val before = valueVr()
      if (before != v) {
        // before.changed -/-> this.changed
        valueVr() = v
        // v     .changed ---> this.changed
//        changed.fire(Output.Update(self, v))
      }
    }

//    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    def connect()(implicit tx: S#Tx): this.type = {
      () // graph.changed ---> changed
      this
    }

    def disconnect()(implicit tx: S#Tx): Unit = {
      () // graph.changed -/-> changed
    }

//    object changed extends Changed
//      with evt.impl.Generator[S, Output.Update[S]] {
//
//      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[Output.Update[S]] =
//        if (pull.parents(this).isEmpty) {
//          Some(pull.resolve[Output.Update[S]])
//        } else {
//          None
//        }
//    }

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] = {
//      val tgtOut  = evt.Targets[Out] // txOut.newId()
//      val idOut   = tgtOut.id
      val idOut   = txOut.newId()
      val fscOut  = context(fscape)
      val vrOut   = txOut.newVar[Option[Obj[Out]]](idOut, None)  // correct to drop cache?
      val out     = new Impl[Out](idOut, fscOut, key, valueType, vrOut)
      out.connect()
    }

    protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      fscape.write(out)
      out.writeUTF(key)
      out.writeInt(valueType.typeId)
      valueVr.write(out)
    }

    protected def disposeData()(implicit tx: S#Tx): Unit = {
      disconnect()
      valueVr.dispose()
    }
  }
}
sealed trait OutputImpl[S <: Sys[S]] extends Output[S] {
  def value(implicit tx: S#Tx): Option[Obj[S]]
  def value_=(v: Option[Obj[S]])(implicit tx: S#Tx): Unit
}