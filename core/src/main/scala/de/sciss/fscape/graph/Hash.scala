/*
 *  Hash.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that produces a continuous hash value, useful for comparisons and debugging applications.
  * The hash is produced using the 64-bit Murmur 2 algorithm, however the continuous output is
  * given without "finalising"  the hash, and it is not initialized with the input's length
  * (as it is unknown at that stage).
  *
  * '''Not yet implemented:''' Only after the input terminates, one additional sample of
  * finalised hash is given (you can use `.last` to only retain the final hash).
  *
  * @param in   the signal to hash.
  */
final case class Hash(in: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args

    if (in.isLong) {
      stream.Hash.fromLong(in.toLong)
    } else if (in.isDouble) {
      stream.Hash.fromDouble(in.toDouble)
    } else {
      stream.Hash.fromInt(in.toInt)
    }
  }
}
