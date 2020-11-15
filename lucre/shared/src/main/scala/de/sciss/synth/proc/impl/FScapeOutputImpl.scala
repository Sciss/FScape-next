/*
 *  FScapeOutputImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.proc.impl

import de.sciss.lucre.impl.{ConstObjImpl, ObjFormat}
import de.sciss.lucre.{AnyTxn, Copy, Elem, Ident, Obj, Txn, Var => LVar}
import de.sciss.serial.{DataInput, DataOutput, TFormat}
import de.sciss.synth.proc.FScape
import de.sciss.synth.proc.FScape.Output

object FScapeOutputImpl {
  private final val SER_VERSION = 0x464F  // "FO"

  sealed trait Update[T]

  def apply[T <: Txn[T]](fscape: FScape[T], key: String, tpe: Obj.Type)(implicit tx: T): FScapeOutputImpl[T] = {
//    val tgt = evt.Targets[T]
//    val id = tgt.id
    val id  = tx.newId()
    val vr  = id.newVar[Option[Obj[T]]](None)
    new Impl[T](id, fscape, key, tpe, vr).connect()
  }

  def read[T <: Txn[T]](in: DataInput)(implicit tx: T): Output[T] =
    format[T].readT(in)

  def format[T <: Txn[T]]: TFormat[T, Output[T]] = anyFmt.asInstanceOf[Fmt[T]]

  implicit def implFmt[T <: Txn[T]]: TFormat[T, FScapeOutputImpl[T]] =
    anyFmt.asInstanceOf[TFormat[T, FScapeOutputImpl[T]]]

  private val anyFmt = new Fmt[AnyTxn]

  private final class Fmt[T <: Txn[T]] extends ObjFormat[T, Output[T]] {
    def tpe: Obj.Type = Output
  }

  def readIdentifiedObj[T <: Txn[T]](in: DataInput)(implicit tx: T): Output[T] = {
    val constCookie = in.readByte()
    if (constCookie != 3)
      sys.error(s"Unexpected cookie (found $constCookie, expected 3)")
//    val tgt     = evt.Targets.read(in, access)
//    val id = tgt.id
    val id = tx.readId(in)
//    val cookie  = in.readByte()
//    if (cookie != 3) sys.error(s"Unexpected cookie, expected 3 found $cookie")
//    val id      = tx.readId(in, access)
    val serVer  = in.readShort()
    if (serVer != SER_VERSION)
      sys.error(s"Incompatible serialized version (found ${serVer.toInt.toHexString}, required ${SER_VERSION.toHexString})")

    val fscape  = FScape.read(in)
    val key     = in.readUTF()
    val tpeId   = in.readInt()
    val tpe     = Obj.getType(tpeId)
    val vr      = id.readVar[Option[Obj[T]]](in)
    new Impl[T](id, fscape, key, tpe, vr)
  }

  private final class Impl[T <: Txn[T]](val id: Ident[T] /* protected val targets: evt.Targets[T] */ ,
                                        val fscape: FScape[T], val key: String, val valueType: Obj.Type,
                                        valueVr: LVar[T, Option[Obj[T]]])
    extends FScapeOutputImpl[T] with ConstObjImpl[T, Any] /* SingleEventNode[T, Output.Update[T]] */ { self =>

    def tpe: Obj.Type = Output

    override def toString: String = s"Output($id, $fscape, $key, $valueType)"

    def value(implicit tx: T): Option[Obj[T]] = valueVr()

    def value_=(v: Option[Obj[T]])(implicit tx: T): Unit = {
      val before = valueVr()
      if (before != v) {
        // before.changed -/-> this.changed
        valueVr() = v
        // v     .changed ---> this.changed
//        changed.fire(Output.Update(self, v))
      }
    }

//    def isConnected(implicit tx: T): Boolean = targets.nonEmpty

    def connect()(implicit tx: T): this.type = {
      () // graph.changed ---> changed
      this
    }

    def disconnect()(implicit tx: T): Unit = {
      () // graph.changed -/-> changed
    }

//    object changed extends Changed
//      with GeneratorEvent[T, Output.Update[T]] {
//
//      def pullUpdate(pull: Pull[T])(implicit tx: T): Option[Output.Update[T]] =
//        if (pull.parents(this).isEmpty) {
//          Some(pull.resolve[Output.Update[T]])
//        } else {
//          None
//        }
//    }

    def copy[Out <: Txn[Out]]()(implicit tx: T, txOut: Out, context: Copy[T, Out]): Elem[Out] = {
//      val tgtOut  = evt.Targets[Out] // txOut.newId()
//      val idOut   = tgtOut.id
      val idOut   = txOut.newId()
      val fscOut  = context(fscape)
      val vrOut   = idOut.newVar[Option[Obj[Out]]](None)  // correct to drop cache?
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

    protected def disposeData()(implicit tx: T): Unit = {
      disconnect()
      valueVr.dispose()
    }
  }
}
sealed trait FScapeOutputImpl[T <: Txn[T]] extends Output[T] {
  def value(implicit tx: T): Option[Obj[T]]
  def value_=(v: Option[Obj[T]])(implicit tx: T): Unit
}