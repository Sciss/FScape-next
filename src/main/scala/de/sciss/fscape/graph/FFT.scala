/*
 *  FFT.scala
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
package graph

import de.sciss.fscape.stream.{OutD, OutI, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

sealed trait FFTUGen extends UGenSource.SingleOut {
  def in      : GE
  def size    : GE
  def padding : GE

  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD

  protected final def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, size.expand, padding.expand))

  protected final def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] final def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, padding) = args
    apply(in = in.toDouble, size = size.toInt, padding = padding.toInt)
  }
}

final case class Real1FFT(in: GE, size: GE, padding: GE = 0) extends FFTUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1FFT(in = in, size = size, padding = padding)
}

final case class Real1IFFT(in: GE, size: GE, padding: GE = 0) extends FFTUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1IFFT(in = in, size = size, padding = padding)
}

final case class Real1FullFFT(in: GE, size: GE, padding: GE = 0) extends FFTUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1FullFFT(in = in, size = size, padding = padding)
}

final case class Real1FullIFFT(in: GE, size: GE, padding: GE = 0) extends FFTUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1FullIFFT(in = in, size = size, padding = padding)
}

final case class Complex1FFT(in: GE, size: GE, padding: GE = 0) extends FFTUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex1FFT(in = in, size = size, padding = padding)
}

final case class Complex1IFFT(in: GE, size: GE, padding: GE = 0) extends FFTUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex1IFFT(in = in, size = size, padding = padding)
}