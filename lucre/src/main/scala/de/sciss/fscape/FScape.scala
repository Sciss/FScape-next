/*
 *  FScape.scala
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

import de.sciss.fscape.impl.{FScapeImpl => Impl}
import de.sciss.lucre.event.Publisher
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.model
import de.sciss.serial.{DataInput, Serializer}

import scala.collection.immutable.{IndexedSeq => Vec}

object FScape extends Obj.Type {
  final val typeID = 0x1000B

  // ---- implementation forwards ----

  /** Registers this type and the graph object type.
    * You can use this call to register all FScape components.
    */
  override def init(): Unit = {
    super.init()
    GraphObj.init()
  }

  def apply[S <: Sys[S]](implicit tx: S#Tx): FScape[S] = Impl[S]

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] = Impl.read(in, access)

  implicit def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, FScape[S]] = Impl.serializer[S]

  // ---- event types ----

  /** An update is a sequence of changes */
  final case class Update[S <: Sys[S]](proc: FScape[S], changes: Vec[Change[S]])

  /** A change is either a state change, or a scan or a grapheme change */
  sealed trait Change[S <: Sys[S]]

  final case class GraphChange[S <: Sys[S]](change: model.Change[Graph]) extends Change[S]

  /** Source code of the graph function. */
  final val attrSource = "graph-source"

  override def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): Obj[S] =
    Impl.readIdentifiedObj(in, access)
}

/** The `FScape` trait is the basic entity representing a sound process. */
trait FScape[S <: Sys[S]] extends Obj[S] with Publisher[S, FScape.Update[S]] {
  /** The variable synth graph function of the process. */
  def graph: GraphObj.Var[S]

}