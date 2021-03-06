/*
 *  Flatten.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.{GE, UGenGraph, UGenInLike}

object Flatten extends ProductReader[Flatten] {
  override def read(in: RefMapIn, key: String, arity: Int): Flatten = {
    require (arity == 1)
    val _elem = in.readGE()
    new Flatten(_elem)
  }
}
/** A graph element that flattens the channels from a nested multi-channel structure.
  *
  * @param elem the element to flatten
  */
final case class Flatten(elem: GE) extends GE.Lazy {
  override def toString = s"$elem.flatten"

  /** Abstract method which must be implemented by creating the actual `UGen`s
    * during expansion. This method is at most called once during graph
    * expansion
    *
    * @return the expanded object (depending on the type parameter `U`)
    */
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UGenInGroup(elem.expand.flatOutputs)
}