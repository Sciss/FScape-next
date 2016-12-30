/*
 *  OutputImpl.scala
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

  def apply[S <: Sys[S]](fscape: FScape[S], key: String, tpe: Obj.Type)(implicit tx: S#Tx): Output[S] = {
    val id = tx.newID()
    new Impl(id, fscape, key, tpe)
  }

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Output[S] =
    serializer[S].read(in, access)

  def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, Output[S]] = anySer.asInstanceOf[Ser[S]]

  private val anySer = new Ser[NoSys]

  private final class Ser[S <: Sys[S]] extends ObjSerializer[S, Output[S]] {
    def tpe: Obj.Type = Output
  }

  def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Output[S] = {
    val cookie  = in.readByte()
    if (cookie != 3) sys.error(s"Unexpected cookie, expected 3 found $cookie")
    val id      = tx.readID(in, access)
    val serVer  = in.readShort()
    if (serVer != SER_VERSION)
      sys.error(s"Incompatible serialized version (found ${serVer.toInt.toHexString}, required ${SER_VERSION.toHexString})")

    val fscape  = FScape.read(in, access)
    val key     = in.readUTF()
    val tpeID   = in.readInt()
    val tpe     = Obj.getType(tpeID)
    new Impl(id, fscape, key, tpe)
  }

  private final class Impl[S <: Sys[S]](val id: S#ID, val fscape: FScape[S], val key: String, val valueType: Obj.Type)
    extends Output[S] with ConstObjImpl[S, Any] {

    def tpe: Obj.Type = Output

    override def toString: String = s"Output($id, $fscape, $key, $valueType)"

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] = {
      val idOut   = txOut.newID()
      val fscOut  = context(fscape)
      val out     = new Impl(idOut, fscOut, key, valueType)
      out // .connect()
    }

    protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      fscape.write(out)
      out.writeUTF(key)
      out.writeInt(valueType.typeID)
    }
  }
}