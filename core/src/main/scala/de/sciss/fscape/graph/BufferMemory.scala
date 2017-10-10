/*
 *  BufferMemory.scala
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

/** Inserts a buffer into the stream of up to `size` frames. */
final case class BufferMemory(in: GE, size: GE) extends GE {
  private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike = {
    val num = (size / ControlBlockSize()).ceil
    Elastic(in, num)
  }
}
