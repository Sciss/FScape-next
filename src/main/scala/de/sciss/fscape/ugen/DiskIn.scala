///*
// *  DiskIn.scala
// *  (FScape)
// *
// *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU General Public License v2+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.fscape
//package ugen
//
//import de.sciss.file._
//import de.sciss.synth.io.AudioFile
//
//import scala.concurrent.blocking
//
//object DiskIn {
//  def apply(file: File): DiskIn = {
//    val af = blocking(AudioFile.openRead(file))
//    new Impl(file, af)
//  }
//
//  private final class Impl(file: File, af: AudioFile) extends DiskIn {
//    override def toString = s"DiskIn(${file.path})@${hashCode.toHexString}"
//
//    private[this] val bufSz = 8192
//    private[this] val buf   = af.buffer(bufSz)
//
//    object output extends UGenIn {
//      def readDouble(frames: Frames, off: Int, len: Int): Int = {
//        val res = math.min(len, af.numFrames - af.position).toInt
//        var rem = res
//        while (rem > 0) {
//          val chunk = math.min(bufSz, rem)
//          af.read(buf, 0, chunk)
//          Util.copy(buf, 0, frames, off, chunk)
//          rem -= chunk
//        }
//        res
//      }
//    }
//
//    def dispose(): Unit = af.cleanUp()
//  }
//}
//sealed trait DiskIn extends UGen {
//  def output: UGenIn
//}
