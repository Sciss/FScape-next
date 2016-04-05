/*
 *  DiskIn.scala
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
package ugen

import de.sciss.synth.io.AudioFile

import scala.concurrent.blocking

object DiskIn {
  def apply(path: String): DiskIn = {
    val af = blocking(AudioFile.openRead(path))
    new Impl(path, af)
  }

  private final class Impl(path: String, af: AudioFile) extends DiskIn {
    override def toString = s"DiskIn($path)@${hashCode.toHexString}"

    private[this] val bufSz = 8192
    private[this] val buf   = af.buffer(bufSz)

    object output extends UGenIn {
      def readDouble(frames: Frames, off: Int, len: Int): Int = {
        var rem = len
        while (rem > 0) {
          val chunk = math.min(bufSz, rem)
          af.read(buf, 0, chunk)
          Util.copy(buf, 0, frames, off, chunk)
          rem -= chunk
        }
        ???
      }
    }

    def dispose(): Unit = af.cleanUp()
  }
}
sealed trait DiskIn extends UGen {
  def output: UGenIn
}
