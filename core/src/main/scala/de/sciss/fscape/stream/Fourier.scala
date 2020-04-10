/*
 *  Fourier.scala
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
package stream

import akka.stream.{Attributes, FanInShape5}
import de.sciss.file.File
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InIAux, InLAux}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeImpl}
import de.sciss.numbers.Implicits._
import de.sciss.numbers.{IntFunctions => ri, LongFunctions => rl}

import scala.math.{max, min}

// XXX TODO --- should have a progress indicator output (and way to stop inside storageFFT)
object Fourier {
  def apply(in: OutD, size: OutL, padding: OutL, dir: OutD, mem: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in     , stage.in0)
    b.connect(size   , stage.in1)
    b.connect(padding, stage.in2)
    b.connect(dir    , stage.in3)
    b.connect(mem    , stage.in4)
    stage.out
  }

  private final val name = "Fourier"

  private type Shp = FanInShape5[BufD, BufL, BufL, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](name) {

    override def toString = s"$name@${hashCode.toHexString}"

    val shape: Shape = new FanInShape5(
      in0 = InD (s"$name.in"     ),
      in1 = InL (s"$name.size"   ),
      in2 = InL (s"$name.padding"),
      in3 = InD (s"$name.dir"    ),
      in4 = InI (s"$name.mem"    ),
      out = OutD(s"$name.out"    )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends FilterWindowedInAOutA[Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize : InLAux = InLAux   (this, shape.in1)(max(1L, _) * 2)
    private[this] val hPad  : InLAux = InLAux   (this, shape.in2)(max(0L, _) * 2)
    private[this] val hDir  : InDAux = InDAux   (this, shape.in3)()
    private[this] val hMem  : InIAux = InIAux   (this, shape.in4)(i => max(2, i).nextPowerOfTwo)

    private[this] val fileBuffers   = new Array[FileBuffer](4)
    private[this] val tempFiles     = new Array[File      ](4)

    private[this] var size      : Long    = _   // already multiplied by `fftInSizeFactor`
    private[this] var padding   : Long    = _   // already multiplied by `fftInSizeFactor`
    private[this] var memAmount : Int     = _
    private[this] var dir       : Double  = _
    private[this] var gain      : Double  = _
    private[this] var fftSize  = 0L          // refreshed as `size + padding`

    override protected def stopped(): Unit = {
      super.stopped()
      freeFileBuffers()
    }

    @inline private def freeInputFileBuffers(): Unit = freeFileBuffers(2)
    @inline private def freeFileBuffers     (): Unit = freeFileBuffers(4)

    private def freeFileBuffers(n: Int): Unit = {
      var i = 0
      while (i < 4) {
        if (i < n && fileBuffers(i) != null) {
          fileBuffers(i).dispose()
          fileBuffers(i) = null
        }
        if (tempFiles(i) != null) {
          tempFiles(i).delete()
          tempFiles(i) = null
        }
        i += 1
      }
    }

    protected def winBufSize: Int = 0

    override protected def readWinSize  : Long = size
    override protected def writeWinSize : Long = fftSize * 2

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hPad.hasNext && hDir.hasNext && hMem.hasNext
      if (ok) {
        val inF = 2
        size    = hSize .next()
        padding = hPad  .next()
        padding = rl.nextPowerOfTwo(size + padding) - size

        val n = (size + padding) / inF
        if (n != fftSize) {
          fftSize = n
        }

        dir   = hDir.next()
        gain  = if (dir > 0) 1.0 / fftSize else 1.0
        memAmount = min(fftSize, hMem.next()).toInt

        // create new buffers
        freeFileBuffers()
        var i = 0
        while (i < 4) {
          fileBuffers(i) = FileBuffer()
          tempFiles  (i) = ctrl.createTempFile()
          i += 1
        }
      }
      ok
    }

    override protected def readIntoWindow(chunk: Int): Unit = {
      // Write first half of input to 0, second half to 1
      val in        = hIn.array
      var inOff0    = hIn.offset
      var chunk0    = chunk
      if (readOff < fftSize) {
        val chunk1 = min(chunk0, fftSize - readOff).toInt
        fileBuffers(0).write(in, inOff0, chunk1)
        inOff0 += chunk1
        chunk0 -= chunk1
      }
      if (chunk0 > 0) {
        fileBuffers(1).write(in, inOff0, chunk0)
      }
      hIn.advance(chunk)
    }

    override protected def writeFromWindow(chunk: Int): Unit = {
      // Read first half of output from 2, second half from 3
      val out       = hOut.array
      val outOff    = hOut.offset
      var outOff0   = outOff
      var chunk0    = chunk
      if (writeOff < fftSize) {
        val chunk1 = math.min(chunk0, fftSize - writeOff).toInt
        fileBuffers(2).read(out, outOff0, chunk1)
        outOff0 += chunk1
        chunk0  -= chunk1
      }
      if (chunk0 > 0) {
        fileBuffers(3).read(out, outOff0, chunk0)
      }
      if (gain != 1.0) {
        Util.mul(out, outOff, chunk, gain) // scale correctly for forward FFT
      }
      hOut.advance(chunk)
    }

    protected def processWindow(): Unit = {
      var zero = (fftSize << 1) - readOff
      if (readOff < fftSize) {
        val chunk1 = math.min(zero, fftSize - readOff)
        fileBuffers(0).writeValue(0.0, chunk1)
        zero -= chunk1
      }
      if (zero > 0) {
        fileBuffers(1).writeValue(0.0, zero)
      }

      storageFFT(fileBuffers, tempFiles, len = fftSize, dir = dir, memAmount = memAmount)

      freeInputFileBuffers()    // we don't need the inputs any longer
    }
  }

  @inline private def swap[A](arr: Array[A], i: Int, j: Int): Unit = {
    val temp  = arr(i)
    arr(i)    = arr(j)
    arr(j)    = temp
  }

  /**
    *	One-dimensional Fourier transform of a large data set stored on external media.
    *	len must be a power of 2. file[ 0...3 ] contains the stream pointers to 4 temporary files,
    *	each large enough to hold half of the data. The input data must have its first half stored
    *	in file file[ 0 ], its second half in file[ 1 ], in native floating point form.
    *	memAmount real numbers are processed per buffered read or write.
    *	Output: the  first half of the result is stored in file[ 2 ], the second half in file[ 3 ].
    *
    *	@param	audioFiles	0 = first half input,  1 = second half input
    *						          2 = first half output, 3 = second half output
    *	@param	len			    complex fft length (power of 2!)
    *	@param	dir			    1 = forward, -1 = inverse transform (multiply by freqShift for special effect!)
    *	@param	memAmount   internal buffer sizes (must be a power of two)
    */
  private def storageFFT(audioFiles: Array[FileBuffer], tempFiles: Array[File], len: Long, dir: Double,
                         memAmount: Int): Unit = {
    require(memAmount >= 2 && ri.isPowerOfTwo(memAmount))
    require(len       >= 2 && rl.isPowerOfTwo(len))

    val indexMap	= Array(1, 0, 3, 2)
    val indices   = new Array[Int](4)
    val buf1      = new Array[Double](memAmount)
    val buf2      = new Array[Double](memAmount)
    val buf3      = new Array[Double](memAmount)

    var mMax	    = len
    val numSteps  = len / memAmount
    val halfSteps = numSteps >> 1
    val thetaBase = dir * math.Pi

    var kd	      = memAmount >> 1
    var n2        = len
    var jk        = len
    var kc	      = 0L

    def rewind(): Unit = {
      audioFiles.foreach(_.rewind())

      swap(audioFiles, 1, 3)
      swap(audioFiles, 0, 2)
      swap(tempFiles , 1, 3)
      swap(tempFiles , 0, 2)

      indices(0) = 2
      indices(1) = 3
      indices(2) = 0
      indices(3) = 1
    }

    rewind()

    // The first phase of the transform starts here.
    do {		// Start of the computing pass.
      val theta	= thetaBase / (len/mMax)
      val tempW = math.sin(theta / 2)
      val wpRe	= -2.0 * tempW * tempW
      val wpIm  = math.sin(theta)
      var wRe		= 1.0
      var wIm		= 0.0
      mMax  >>= 1

      var i = 0
      while (i < 2) {
        var step = 0
        do {
          audioFiles(indices(0)).read(buf1, 0, memAmount)
          audioFiles(indices(1)).read(buf2, 0, memAmount)

          var j = 0
          while (j < memAmount) {
            val h       = j + 1
            val tempRe  = wRe * buf2(j) - wIm * buf2(h)
            val tempIm  = wIm * buf2(j) + wRe * buf2(h)
            buf2(j)     = buf1(j) - tempRe
            buf1(j)    += tempRe
            buf2(h)     = buf1(h) - tempIm
            buf1(h)    += tempIm
            j += 2
          }

          kc += kd
          if (kc == mMax) {
            kc = 0L
            val tempW = wRe
            wRe += tempW * wpRe - wIm * wpIm
            wIm += tempW * wpIm + wIm * wpRe
          }

          audioFiles(indices(2)).write(buf1, 0, memAmount)
          audioFiles(indices(3)).write(buf2, 0, memAmount)

          step += 1
        } while (step < halfSteps)

        if ((i == 0) && (n2 != len) && (n2 == memAmount)) {
          indices(0) = indexMap(indices(0))
          indices(1) = indices(0)
        }

        if (halfSteps == 0) i = 2
        else i += 1
      }

      rewind()        // Start of the permutation pass.
      jk >>= 1
      if (jk == 1) {
        assert(assertion = false, "We never get here?")
        mMax    = len
        jk      = len
      }

      n2 >>= 1
      if (n2 > memAmount) {
        var i = 0
        while (i < 2) {
          var step = 0L
          while (step < numSteps) {
            var n1 = 0L
            while (n1 < n2) {
              audioFiles(indices(0)).read (buf1, 0, memAmount)
              audioFiles(indices(2)).write(buf1, 0, memAmount)
              n1 += memAmount
            }
            indices(2) = indexMap(indices(2))

            step += n2 / memAmount
          }
          indices(0) = indexMap(indices(0))

          i += 1
        }
        rewind()

      } else if (n2 == memAmount) {
        indices(1) = indices(0)
      }

    } while (n2 >= memAmount)

    // System.out.println( "part 2" );
    var j = 0
    // The second phase of the transform starts here. Now, the remaining permutations
    // are sufficiently local to be done in place.

    do {
      val theta	= thetaBase / (len/mMax)
      val tempW	= math.sin(theta / 2)
      val wpRe	= -2.0 * tempW * tempW
      val wpIm	= math.sin(theta)
      var wRe		= 1.0
      var wIm		= 0.0
      mMax  >>= 1
      val ks		= kd
      kd	  >>= 1

      var i = 0
      while (i < 2) {
        var step = 0
        while (step < numSteps) {
          audioFiles(indices(0)).read(buf3, 0, memAmount)

          var kk = 0
          var k  = ks

          do {
            val h       = kk + ks + 1
            val tempRe  = wRe * buf3(kk + ks) - wIm * buf3(h)
            val tempIm  = wIm * buf3(kk + ks) + wRe * buf3(h)
            buf1(j)     = buf3(kk) + tempRe
            buf2(j)     = buf3(kk) - tempRe
            j  += 1
            kk += 1
            buf1(j) = buf3(kk) + tempIm
            buf2(j) = buf3(kk) - tempIm
            j  += 1
            kk += 1

            if (kk >= k) {
              kc += kd
              if (kc == mMax) {
                kc        = 0L
                val tempW = wRe
                wRe      += tempW * wpRe - wIm * wpIm
                wIm      += tempW * wpIm + wIm * wpRe
              }

              kk += ks
              k   = kk + ks
            }
          } while (kk < memAmount)

          // flush
          if (j == memAmount) {
            audioFiles(indices(2)).write(buf1, 0, memAmount)
            audioFiles(indices(3)).write(buf2, 0, memAmount)
            j = 0
          }
          step += 1
        } // for steps
        indices(0) = indexMap(indices(0))

        i += 1
      } // for (0 until 1)

      rewind()
      jk >>= 1
    } while (jk > 1)
  }
}