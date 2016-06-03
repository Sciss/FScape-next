///*
// *  DiskOut.scala
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
//import de.sciss.file.File
//import de.sciss.synth.io.{AudioFile, AudioFileSpec}
//
//import scala.concurrent.blocking
//
//object DiskOut {
//  def apply(file: File, spec: AudioFileSpec, in: UGenIn): DiskOut = {
//    val af = blocking(AudioFile.openWrite(file, spec))
//    new Impl(file = file, af = af, in = in)
//  }
//
//  private final class Impl(file: File, af: AudioFile, in: UGenIn) extends DiskOut {
//    def dispose(): Unit = af.cleanUp()
//  }
//}
//sealed trait DiskOut extends UGen