/*
 *  Real1FFT.scala
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

/** Real-valued one-dimensional forward fast fourier transform.
  *
  * @param in       the input signal to transform
  * @param size     the size of chunks to take from the input
  * @param padding  the number of frames to pad chunks with before transforming.
  *                 thus fft size = size + padding
  */
case class Real1FFT(in: GE, size: GE, padding: GE = 0) extends UGenSource.SingleOut {
  protected def makeSignal: UGenIn = {
    ???
//    val p = ugen.Real1FFT(in = in.expand, size = size.expand, padding = padding.expand)
//    p.output
  }
}