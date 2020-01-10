/*
 *  Loudness.scala
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
import de.sciss.fscape.stream.impl.{FilterIn5DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object Loudness {
  var DEBUG = false

  def apply(in: OutD, sampleRate: OutD, size: OutI, spl: OutD, diffuse: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(sampleRate, stage.in1)
    b.connect(size      , stage.in2)
    b.connect(spl       , stage.in3)
    b.connect(diffuse   , stage.in4)
    stage.out
  }

  private final val name = "Loudness"

  private type Shape = FanInShape5[BufD, BufD, BufI, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape5(
      in0 = InD (s"$name.in"        ),
      in1 = InD (s"$name.sampleRate"),
      in2 = InI (s"$name.size"      ),
      in3 = InD (s"$name.spl"       ),
      in4 = InI (s"$name.diffuse"   ),
      out = OutD(s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn5DImpl[BufD, BufD, BufI, BufD, BufI] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var winSize   : Int           = _
    private[this] var spl       : Double        = _
    private[this] var diffuse   : Boolean       = _
    private[this] var sampleRate: Double        = _

    private[this] val LT = new Array[Double](28)

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        sampleRate = bufIn1.buf(inOff)
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        winSize = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        spl = math.max(1, bufIn3.buf(inOff))
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        diffuse = bufIn4.buf(inOff) > 0
      }
      if (winSize != oldSize) {
        winBuf = new Array(winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val lt    = LT
      val _spl  = spl

      zwickerBandsBody(winBuf, numFrames = writeToWinOff.toInt, sampleRate = sampleRate, LT = lt)

      var i = 0
      while (i < lt.length) {
        lt(i) += _spl
        i += 1
      }

      val res   = zwicker(lt, diffuse = diffuse)
      winBuf(0) = res
      1
    }
  }

  //////////////////////////////////////////////

  /*
      quick and dirty translation and adaptation to Scala by H.H.Rutz 20-Aug-2017

      Original copyright below:



  Zwicker's Loudness Calculation Method SW Tools - V.Portet - (C) 2010
  Partially ported from "Program for calculating loudness according to DIN 45631 (ISO 532B)"
  provided by the "Journal of Acoustical Society of Japan" (1991)
  (And freely accessible on-line via the Osaka University's Website)

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.

  2. The origin of this software must not be misrepresented; you must
     not claim that you wrote the original software.  If you use this
     software in a product, an acknowledgment in the product
     documentation would be appreciated but is not required.

  3. Altered source versions must be plainly marked as new different
     versions, and must not be misrepresented as being the original
     software.

  4. In case of use of some code parts provided herein to build a
     significantly different project, the resulting project must be
     open source as well, and reference to the original code parts
     must be done within the new project source.


  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS
  OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


  V.Portet - Paris, FR.
  vincentportet_95@hotmail.com
  Version 0.10 of the 13th of April 2010


   */

  private[this] final val Q                 = 12.0
  private[this] final val RATE_CONV         = 3509.0

  // Zwicker 1/3rd octave bands
  //  private[this] val FR = Array[Int](
  //    25,31  ,40,50,63,80,100,125,160,200,250,315,400,500,630,800,1000,1250,1600,2000,2500,3150,4000,5000,6300,8000,10000,12500)

  private[this] val FR = Array[Double](
    25,31.5,40,50,63,80,100,125,160,200,250,315,400,500,630,800,1000,1250,1600,2000,2500,3150,4000,5000,6300,8000,10000,12500)

  // LT: must be Array of size 28, this is the result
  private def zwickerBandsBody(buf: Array[Double], numFrames: Int, sampleRate: Double, LT: Array[Double]): Unit = {
    import math.exp

    var pkdEnable   = false
    val enableStart = numFrames / 20
    val enableStop  = numFrames - enableStart

    // Perform 28 steps to define the 28 normalized LT[i]'s...
    //    println("\nStep 2: Normalized data, three-band filter and scanning...\n")
    var passCounter = 0
    while (passCounter < 28) {
      // Initialize 3-band filter (params = f( FR[] ) )
      val f0            = FR(passCounter) // .toDouble
      val K1            = 6.28 * f0 *  Q / sampleRate
      val K3            = 6.28 * f0 / (Q * sampleRate)
      val K2            = 1.0 - K3
      val K4            = 1.0 - exp(-0.9 * f0 / RATE_CONV)
      val ampRatio      = 0.55 / Q
      // Initial conditions
      var Uout          = buf(0)
      var U1st          = Uout
      var du           = 0.0
      var pkMaxMid      = Double.NegativeInfinity
      var pkMinMid      = Double.PositiveInfinity

      // *** Filter and scan the 3 EQ bands ***
      var frame = 0
      while (frame < numFrames) {
        // Enable / disable peak detectors
        pkdEnable = frame > enableStart && frame < enableStop

        // *** Left ch. ***
        val Uin = buf(frame)
        // Start resonant filter differential equation
        du  += (K1 * (Uin - Uout))
        Uout = (K2 * Uout) + (K3 * du)
        // Start first order filter U1st = f(Uin)
        U1st += (K4*(Uin - U1st))

        if(pkdEnable) {
          // Subtract U1st to resonator output (improve rejection at LF and no effect in HF)
          val temp = ampRatio * (Uout - U1st)

          // Peak detectors
          if (temp > pkMaxMid) {
            pkMaxMid = temp
          }
          if (temp < pkMinMid) {
            pkMinMid = temp
          }
        }

        frame += 1
      }
      // Convert peak-peak amplitude to LT[]

      if(pkMaxMid == pkMinMid) {
        pkMaxMid = pkMinMid + 1
      }
      // Returns normalized amplitudes (0 dB corresponds to mid-amp)
      import de.sciss.numbers.Implicits._
      LT(passCounter) = (pkMaxMid - pkMinMid).ampDb

      passCounter += 1
    }
    // LT
  }

  // CONSTANT TABLES

  // Center frequencies of 1/3 oct. bands (FR)

  // Ranges of 1/3 oct. levels for correction at low frequencies according to equal loudness contours (RAP)
  private[this] val RAP = Array[Double](45,55,65,71,80,90,100,120)

  // Reduction of 1/3 oct. band levels at low frequencies according to equal loudness contours within the
  // eight ranges defined by RAP (DLL)
  private[this] val DLL = Array[Array[Double]](
    Array[Double](-32,-24,-16,-10,-5,0,-7,-3,0,-2,0),Array[Double](-29,-22,-15,-10,-4,0,-7,-2,0,-2,0),
    Array[Double](-27,-19,-14, -9,-4,0,-6,-2,0,-2,0),Array[Double](-25,-17,-12, -9,-3,0,-5,-2,0,-2,0),
    Array[Double](-23,-16,-11, -7,-3,0,-4,-1,0,-1,0),Array[Double](-20,-14,-10, -6,-3,0,-4,-1,0,-1,0),
    Array[Double](-18,-12, -9, -6,-2,0,-3,-1,0,-1,0),Array[Double](-15,-10, -8, -4,-2,0,-3,-1,0,-1,0)
  )

  // Critical band level at absolute threshold without taking into account the transmission
  // characteristics of the ear (LTQ)
  private[this] val LTQ = Array[Double](30,18,12,8,7,6,5,4,3,3,3,3,3,3,3,3,3,3,3,3)

  // Correction of levels according to the transmission characteristics of the ear (AO)
  private[this] val AO = Array[Double](0,0,0,0,0,0,0,0,0,0,-0.5,-1.6,-3.2,-5.4,-5.6,-4,-1.6,2,5,12)

  // Level difference between free and diffuse sound fields (DDF)
  private[this] val DDF = Array[Double](0,0,0.5,0.9,1.2,1.6,2.3,2.8,3,2,0,-1.4,-2,-1.9,-1,0.5,3,4,4.3,4)

  // Adaptation of 1/3 oct. band levels to the corresponding critical band level (DCB)
  private[this] val DCB = Array[Double](-0.25,-0.6,-0.8,-0.8,-0.5,0,0.5,1.1,1.5,1.7,1.8,1.8,1.7,1.6,1.4,1.2,0.8,0.5,0,-0.5)

  // Upper limits of approximated critical bands in terms of critical band rate (ZUP)
  private[this] val ZUP = Array[Double](0.9,1.8,2.8,3.5,4.4,5.4,6.6,7.9,9.2,10.6,12.3,13.6,15.2,16.7,16.1,19.3,20.6,21.8,22.7,23.6,24.0)

  // Range of specific loudness for the determination of the steepness of the upper slopes in the specific loudness
  // Critical band rate pattern (RNS)
  private[this] val RNS = Array[Double](21.5,18,15.1,11.5,9,6.1,4.4,3.1,2.13,1.36,0.82,0.42,0.30,0.22,0.15,0.1,0.035,0)

  // Steepness of the upper slopes in the specific loudness - Critical band rate pattern for the ranges
  // RNS as a function of the number
  // of the critical band (USL)
  private[this] val USL = Array[Array[Double]](
    Array(13 ,9  ,7.8,6.2,4.5,3.7 ,2.9,2.4 ,1.95,1.5 ,0.72,0.59,0.40,0.27,0.16,0.12,0.09,0.06),
    Array(8.2,7.5,6.7,5.4,3.8,3   ,2.3,1.7 ,1.45,1.2 ,0.07,0.53,0.33,0.21,0.16,0.11,0.08,0.05),
    Array(6.3,6  ,5.6,4.6,3.6,2.8 ,2.1,1.5 ,1.3 ,0.94,0.64,0.51,0.26,0.2 ,0.14,0.1 ,0.07,0.03),
    Array(5.5,5.1,4.8,4  ,3.2,2.35,1  ,1.35,1.15,0.86,0.63,0.5 ,0.24,0.18,0.12,0.08,0.06,0.02),
    Array(5.5,4.5,4.4,3.5,2.9,2.2 ,1.8,1.3 ,1.1 ,0.82,0.62,0.42,0.22,0.17,0.11,0.08,0.06,0.02),
    Array(5.5,4.5,3.9,3.2,2.7,2.2 ,1.7,1.3 ,1.1 ,0.82,0.62,0.42,0.22,0.17,0.11,0.08,0.06,0.02),
    Array(5.5,4.5,3.9,3.2,2.7,2.2 ,1.7,1.3 ,1.1 ,0.82,0.62,0.42,0.22,0.17,0.11,0.08,0.06,0.02),
    Array(5.5,4.5,3.9,3.2,2.7,2.2 ,1.7,1.3 ,1.1 ,0.82,0.62,0.42,0.22,0.17,0.11,0.08,0.05,0.02)
  )

  private def zwicker(LT: Array[Double], diffuse: Boolean): Double = {
    import math.{log, log10, max, min, pow}

    val GI  = new Array[Double](  3)
    val LE  = new Array[Double]( 21)
    val LCB = new Array[Double](  3)
    val NM  = new Array[Double]( 21)
    val NS  = new Array[Double](270)
    val TI  = new Array[Double]( 11)

    val S   = 0.25
    var XP  = 0.0

    // Correction of 1/3 oct. band levels according to equal loudness contours (XP) and calculation
    // of the intensities for 1/3 oct. bands up to 315Hz
    var i1 = 0
    while (i1 < 11) {
      var j = 0
      if (LT(i1) <= (RAP(j) - DLL(j)(i1))) {
        XP = LT(i1) + DLL(j)(i1)
        TI(i1) = pow(10.0, 0.1 * XP)
      } else {
        j += 1
        if(j >= 7) {
          XP = LT(i1) + DLL(j)(i1)
          TI(i1) = pow(10.0, 0.1 * XP)
        } else {
          var flagOut = false
          while (j < 7 && !flagOut) {
            if (LT(i1) <= RAP(j) - DLL(j)(i1)) {
              XP=LT(i1) + DLL(j)(i1)
              TI(i1) = pow(10.0, 0.1 * XP)
              flagOut=true
            } else {
              j += 1
            }
          }
          if (!flagOut) {
            XP=LT(i1) + DLL(j)(i1)
            TI(i1)=pow(10.0, 0.1 * XP)
          }
        }
      }
      i1 += 1
    }

    // Determination of levels LCB(0-2) within the first three critical bands
    GI(0) = TI(0) + TI(1) + TI(2) + TI(3) + TI(4) + TI(5)
    GI(1) = TI(6) + TI(7) + TI(8)
    GI(2) = TI(9) + TI(10)

    var i2 = 0
    while (i2 < 3) {
      if (GI(i2) > 0.0000001) {
        LCB(i2) = 10.0 * log10(GI(i2))
      } else {
        LCB(i2) = -70.0
      }
      i2 += 1
    }

    // Calculation of main loudness
    var i3 = 0
    while (i3 < 20) {
      LE(i3) = LT(i3 + 7)
      if (i3 <= 2) {
        LE(i3) = LCB(i3)
      }
      LE(i3) -= AO(i3)
      NM(i3) = 0.0
      if (diffuse) {
        LE(i3) += DDF(i3)
      }
      if (LE(i3) > LTQ(i3)) {
        LE(i3) -= DCB(i3)
        // S = .25
        val MP1 = 0.0635 * pow(10.0, 0.025 * LTQ(i3))
        val MP2 = pow(1.0 - S + S * pow(10.0, 0.1 * (LE(i3) - LTQ(i3))), 0.25) - 1.0
        NM(i3) = max(0.0, MP1 * MP2)
      }
      i3 += 1
    }
    NM(20) = 0

    // Correction of specific loudness in the lowest critical band taking into account the dependence
    // of absolute threshold within this critical band
    val KORRY = min(1.0, 0.4 + 0.32 * pow(NM(0), 0.2))
    NM(0) *= KORRY

    // Start values
    var N  = 0.0
    var Z1 = 0.0
    var N1 = 0.0
    var IZ = 0
    var Z  = 0.1

    var Z2 = 0.0
    var N2 = 0.0
    var DZ = 0.0
    var K  = 0.0

    val IZ_MAX = 0 // XXX TODO --- this is never adjusted in the original algorithm, probably a bug

    var j = 0

    // Step to first and subsequent critical bands
    var i4 = 0
    while (i4 < 21) {

      ZUP(i4) += 0.0001
      val IG = min(7, i4 - 1)

      do {
        if (N1 <= NM(i4)) {
          if (N1 != NM(i4)) {
            // Determination of the number J corresponding to the range of specific loudness
            j = 0
            var flagIn = true
            while(flagIn && j < 18) {
              if (RNS(j) < NM(i4)) {
                flagIn = false
              } else {
                j += 1
              }
            }
          }

          // Contribution of unmasked main loudness to total loudness and calculation of values NS(IZ) with a spacing of Z = IZ * 0.1 BARK
          Z2 = ZUP(i4)
          N2 = NM(i4)
          N += (N2 * (Z2 - Z1))
          K = Z
          while (K < (Z2 + 0.1)) {
            NS(IZ) = N2
            if(IZ < 269) {
              IZ += 1
            } else {
              if (DEBUG) println("WARNING ! NS Table overflows during calculation of the contribution of unmasked loudness !")
            }
            K += 0.1
          }
          K = Z2 + 0.1
          Z = K
          IZ = IZ_MAX

        } else {
          // Decision whether the critical band in question is completely or partly masked by accessory loudness
          N2 = RNS(j)
          if (N2 < NM(i4)) {
            N2 = NM(i4)
          }
          DZ = (N1 - N2) / USL(IG)(j)
          Z2 = Z1 + DZ
          if (Z2 > ZUP(i4)) {
            Z2 = ZUP(i4)
            DZ = Z2 - Z1
            N2 = N1 - (DZ * USL(IG)(j))
          }
          // Contribution of accessory loudness to total loudness
          N += DZ * (N1 + N2) / 2.0
          K = Z
          while (K < Z2 + 0.1) {
            NS(IZ) = N1 - ((K - Z1) * USL(IG)(j))
            if (IZ < 269) {
              IZ += 1
            } else {
              if (DEBUG) println("WARNING ! NS Table overflows during calculation of the contribution of accessory loudness !")
            }
            K += 0.1
          }
          K = Z2 + 0.1
          Z = K
          IZ = IZ_MAX
        }

        // Step to next segment
        while (N2 <= RNS(j) && j < 17) {
          j += 1
        }
        if (j >= 17 && N2 <= RNS(j)) {
          j = 17
        }
        Z1 = Z2
        N1 = N2
      }
      while (Z1 < ZUP(i4))

      i4 += 1
    }

    // End of critical band processing
    if (N < 0.0) {
      N = 0.0
    }
    // Calculation of loudness level for LN < 40 Phon or N < 1 Sone
    var LN = max(3.0, 40.0 * pow(N + 0.0005, 0.35))

    // Calculation of loudness level for LN >= 40 Phon or N >= 1 Sone
    if (N >= 1.0) {
      LN = (10.0 * log(N) / log(2)) + 40.0
    }

    LN
  }
}