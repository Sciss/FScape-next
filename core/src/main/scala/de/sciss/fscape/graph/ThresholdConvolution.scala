///*
// *  ThresholdConvolution.scala
// *  (FScape)
// *
// *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU General Public License v2+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.fscape
//package graph
//
//import de.sciss.fscape.UGenSource.unwrap
//import de.sciss.fscape.stream.{StreamIn, StreamOut}
//
//import scala.collection.immutable.{IndexedSeq => Vec}
//
///** @param in       input signal to be convolved
//  * @param kernel   filter kernel (will be read only once)
//  * @param size     length of filter kernel (static)
//  * @param thresh   threshold for input samples within kernel range.
//  *                 convolution is only applied to those frames
//  *                 whose absolute difference to the centre inter
//  * @param boundary a trigger signal that determines logical
//  *                 "boundaries" in the input signal. The convolution
//  *                 is truncated to the left and right of the boundary
//  *                 positions, making it possible to perform
//  *                 convolutions on windowed signals.
//  */
//final case class ThresholdConvolution(in: GE, kernel: GE, size: GE, thresh: GE = 0.0, boundary: GE = 0)
//  extends UGenSource.SingleOut {
//
//  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
//    unwrap(this, Vector(in.expand, kernel.expand, size.expand, thresh.expand, boundary.expand))
//
//  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
//    UGen.SingleOut(this, args)
//
//  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
//    val Vec(in, kernel, size, thresh, boundary) = args
//    stream.ThresholdConvolution(in = in.toDouble, kernel = kernel.toDouble, size = size.toInt,
//      thresh = thresh.toDouble, boundary = boundary.toInt)
//  }
//}