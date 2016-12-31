/*
 *  ChannelProxy.scala
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
package graph

/** Straight outta ScalaCollider. */
final case class ChannelProxy(elem: GE, index: Int) extends GE.Lazy {
  override def toString = s"$elem.\\($index)"

  def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val _elem = elem.expand
    _elem.unwrap(index)
  }
}