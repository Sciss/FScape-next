/*
 *  AbstractClipFoldWrap.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.FanInShape3

abstract class AbstractClipFoldWrapI(name: String, shape: FanInShape3[BufI, BufI, BufI, BufI])(implicit ctrl: Control)
  extends NodeImpl(name, shape)
    with FilterChunkImpl[BufI, BufI, FanInShape3[BufI, BufI, BufI, BufI]]
    with FilterIn3Impl[BufI, BufI, BufI, BufI] {

  private[this] var inVal: Int = _
  private[this] var loVal: Int = _
  private[this] var hiVal: Int = _
  
  protected final def allocOutBuf0(): BufI = ctrl.borrowBufI()

  protected def op(inVal: Int, loVal: Int, hiVal: Int): Int

  protected final def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
    var inOffI  = inOff
    var outOffI = outOff
    val pStop   = inOffI + chunk
    val inArr   = /* if (bufIn0 == null) null else */ bufIn0.buf
    val loArr   = if (bufIn1 == null) null else bufIn1.buf
    val hiArr   = if (bufIn2 == null) null else bufIn2.buf
    val inStop  = /* if (a == null) 0 else */ bufIn0.size
    val loStop  = if (loArr == null) 0 else   bufIn1.size
    val hiStop  = if (hiArr == null) 0 else   bufIn2.size
    val out     = bufOut0.buf
    var _inVal  = inVal
    var _loVal  = loVal
    var _hiVal  = hiVal
    while (inOffI < pStop) {
      if (inOffI < inStop) _inVal = inArr(inOffI)
      if (inOffI < loStop) _loVal = loArr(inOffI)
      if (inOffI < hiStop) _hiVal = hiArr(inOffI)
      out(outOffI) = op(_inVal, _loVal, _hiVal)
      inOffI  += 1
      outOffI += 1
    }
    inVal = _inVal
    loVal = _loVal
    hiVal = _hiVal
  }
}

abstract class AbstractClipFoldWrapL(name: String, shape: FanInShape3[BufL, BufL, BufL, BufL])(implicit ctrl: Control)
  extends NodeImpl(name, shape)
    with FilterChunkImpl[BufL, BufL, FanInShape3[BufL, BufL, BufL, BufL]]
    with FilterIn3Impl[BufL, BufL, BufL, BufL] {

  private[this] var inVal: Long = _
  private[this] var loVal: Long = _
  private[this] var hiVal: Long = _
  
  protected final def allocOutBuf0(): BufL = ctrl.borrowBufL()

  protected def op(inVal: Long, loVal: Long, hiVal: Long): Long

  protected final def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
    var inOffI  = inOff
    var outOffI = outOff
    val pStop   = inOffI + chunk
    val inArr   = /* if (bufIn0 == null) null else */ bufIn0.buf
    val loArr   = if (bufIn1 == null) null else bufIn1.buf
    val hiArr   = if (bufIn2 == null) null else bufIn2.buf
    val inStop  = /* if (a == null) 0 else */ bufIn0.size
    val loStop  = if (loArr == null) 0 else   bufIn1.size
    val hiStop  = if (hiArr == null) 0 else   bufIn2.size
    val out     = bufOut0.buf
    var _inVal  = inVal
    var _loVal  = loVal
    var _hiVal  = hiVal
    while (inOffI < pStop) {
      if (inOffI < inStop) _inVal = inArr(inOffI)
      if (inOffI < loStop) _loVal = loArr(inOffI)
      if (inOffI < hiStop) _hiVal = hiArr(inOffI)
      out(outOffI) = op(_inVal, _loVal, _hiVal)
      inOffI  += 1
      outOffI += 1
    }
    inVal = _inVal
    loVal = _loVal
    hiVal = _hiVal
  }
}

abstract class AbstractClipFoldWrapD(name: String, shape: FanInShape3[BufD, BufD, BufD, BufD])(implicit ctrl: Control)
  extends NodeImpl(name, shape)
    with FilterChunkImpl[BufD, BufD, FanInShape3[BufD, BufD, BufD, BufD]]
    with FilterIn3Impl[BufD, BufD, BufD, BufD] {

  private[this] var inVal: Double = _
  private[this] var loVal: Double = _
  private[this] var hiVal: Double = _

  protected final def allocOutBuf0(): BufD = ctrl.borrowBufD()

  protected def op(inVal: Double, loVal: Double, hiVal: Double): Double

  protected final def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
    var inOffI  = inOff
    var outOffI = outOff
    val pStop   = inOffI + chunk
    val inArr   = /* if (bufIn0 == null) null else */ bufIn0.buf
    val loArr   = if (bufIn1 == null) null else bufIn1.buf
    val hiArr   = if (bufIn2 == null) null else bufIn2.buf
    val inStop  = /* if (a == null) 0 else */ bufIn0.size
    val loStop  = if (loArr == null) 0 else   bufIn1.size
    val hiStop  = if (hiArr == null) 0 else   bufIn2.size
    val out     = bufOut0.buf
    var _inVal  = inVal
    var _loVal  = loVal
    var _hiVal  = hiVal
    while (inOffI < pStop) {
      if (inOffI < inStop) _inVal = inArr(inOffI)
      if (inOffI < loStop) _loVal = loArr(inOffI)
      if (inOffI < hiStop) _hiVal = hiArr(inOffI)
      out(outOffI) = op(_inVal, _loVal, _hiVal)
      inOffI  += 1
      outOffI += 1
    }
    inVal = _inVal
    loVal = _loVal
    hiVal = _hiVal
  }
}