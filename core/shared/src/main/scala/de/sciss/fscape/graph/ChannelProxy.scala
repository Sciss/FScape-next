/*
 *  ChannelProxy.scala
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

object ChannelProxy extends ProductReader[ChannelProxy] {
  override def read(in: RefMapIn, key: String, arity: Int): ChannelProxy = {
    require (arity == 2)
    val _elem   = in.readGE()
    val _index  = in.readInt()
    new ChannelProxy(_elem, _index)
  }
}
/** Straight outta ScalaCollider. */
final case class ChannelProxy(elem: GE, index: Int) extends GE.Lazy {
  override def toString = s"$elem.\\($index)"

  def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val _elem = elem.expand
    _elem.unwrap(index)
  }
}