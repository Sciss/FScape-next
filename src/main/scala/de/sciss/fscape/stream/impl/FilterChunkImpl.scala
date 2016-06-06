package de.sciss.fscape.stream
package impl

import akka.stream.stage.GraphStageLogic
import akka.stream.{Inlet, Outlet, Shape}

trait FilterChunkImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, S <: Shape] {
  _: InOutImpl[S] with GraphStageLogic =>

  // ---- abstract ----

  protected var bufIn0: In0
  protected var bufOut: Out

  protected def allocOutBuf(): Out

  protected def in0: Inlet [In0]
  protected def out: Outlet[Out]

  protected def processChunk(inOff: Int, outOff: Int, len: Int): Int

  // ---- impl ----

  private[this] var inOff             = 0  // regarding `bufIn`
  private[this] var inRemain          = 0
  private[this] var outOff            = 0  // regarding `bufOut`
  private[this] var outRemain         = 0
  private[this] var outSent           = true

  @inline
  private[this] def shouldRead = inRemain == 0 && canRead

  def process(): Unit = {
    var stateChange = false

    if (shouldRead) {
      readIns()
      inRemain    = bufIn0.size
      inOff       = 0
      stateChange = true
    }

    if (outSent) {
      bufOut        = allocOutBuf()
      outRemain     = bufOut.size
      outOff        = 0
      outSent       = false
      stateChange   = true
    }

    val chunk = math.min(inRemain, outRemain)
    if (chunk > 0) {
      val chunk1   = processChunk(inOff = inOff, outOff = outOff, len = chunk)
      inOff       += chunk1
      inRemain    -= chunk1
      outOff      += chunk1
      outRemain   -= chunk1
      if (chunk1 > 0) stateChange = true
    }

    val flushOut = inRemain == 0 && isClosed(in0)
    if (!outSent && (outRemain == 0 || flushOut) && isAvailable(out)) {
      if (outOff > 0) {
        bufOut.size = outOff
        push(out, bufOut)
      } else {
        bufOut.release()
      }
      bufOut      = null
      outSent     = true
      stateChange = true
    }

    if      (flushOut && outSent) completeStage()
    else if (stateChange)         process()
  }
}