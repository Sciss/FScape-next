/*
 *  Fourier.scala
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
package stream

import de.sciss.file.File
import de.sciss.fscape.stream.impl.FileBuffer

object Fourier {
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
    *	@param	buf			    3 equal sized buffers
    */
  private def storageFFT(audioFiles: Array[FileBuffer], tempFiles: Array[File], len: Long, dir: Double,
                         buf: Array[Array[Double]]): Unit = {

    val indexMap	= Array(1, 0, 3, 2)
    val indices   = new Array[Int](4)
    val buf1      = buf(0)
    val buf2      = buf(1)
    val buf3      = buf(2)
    val memAmount = buf1.length

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
          if (j >= memAmount) {
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