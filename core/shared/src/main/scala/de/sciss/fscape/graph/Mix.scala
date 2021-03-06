/*
 *  Mix.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}

import scala.collection.immutable.{IndexedSeq => Vec}

object Mix {
  object MonoEqP extends ProductReader[MonoEqP] {
    override def read(in: RefMapIn, key: String, arity: Int): MonoEqP = {
      require (arity == 1)
      val _elem = in.readGE()
      new MonoEqP(_elem)
    }
  }
  final case class MonoEqP(elem: GE) extends GE.Lazy {
    override def productPrefix = s"Mix$$MonoEqP"

    override def toString = s"$productPrefix($elem)"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val flat = elem.expand.flatOutputs
      makeUGen(flat)
    }
  }

  private def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
    val sz = args.size
    if (sz == 0) UGenInGroup.empty else {
      import BinaryOp.{Plus, Times}
      val sum = args.tail.foldLeft[GE](args.head)(Plus.make(_, _))
      if (sz == 1) sum else Times.make(sum, math.sqrt(1.0 / sz))
    }
  }
}