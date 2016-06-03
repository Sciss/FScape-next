///*
// *  Real1FFT.scala
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
//object Real1FFT {
//  def apply(in: UGenIn, size: UGenIn, padding: UGenIn = 0): Real1FFT =
//    new Impl(in = in, size = size, padding = padding)
//
//  private final class Impl(in: UGenIn, size: UGenIn, padding: UGenIn) extends Real1FFT {
//    object output extends UGenIn {
//      def readDouble(frames: Frames, off: Int, len: Int): Int = {
//        ???
//      }
//    }
//
//    def dispose() = ()
//  }
//}
//sealed trait Real1FFT extends UGen {
//  def output: UGenIn
//}