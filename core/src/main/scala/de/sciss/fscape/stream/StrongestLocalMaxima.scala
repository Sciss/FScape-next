/*
 *  StrongestLocalMaxima.scala
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

import akka.stream.{Attributes, Inlet}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedMultiInOut
import de.sciss.fscape.stream.impl.shapes.In7Out2Shape
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.math.{max, min}

object StrongestLocalMaxima {
  def apply(in: OutD, size: OutI, minLag: OutI, maxLag: OutI, thresh: OutD, octaveCost: OutD, num: OutI)
           (implicit b: Builder): (OutD, OutD) = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(minLag    , stage.in2)
    b.connect(maxLag    , stage.in3)
    b.connect(thresh    , stage.in4)
    b.connect(octaveCost, stage.in5)
    b.connect(num       , stage.in6)

    (stage.out0, stage.out1)
  }

  private final val name = "StrongestLocalMaxima"

  private type Shp = In7Out2Shape[BufD, BufI, BufI, BufI, BufD, BufD, BufI,   BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = In7Out2Shape(
      in0   = InD (s"$name.in"        ),
      in1   = InI (s"$name.size"      ),
      in2   = InI (s"$name.minLag"    ),
      in3   = InI (s"$name.maxLag"    ),
      in4   = InD (s"$name.thresh"    ),
      in5   = InD (s"$name.octaveCost"),
      in6   = InI (s"$name.n"         ),
      out0  = OutD(s"$name.lags"      ),
      out1  = OutD(s"$name.strengths" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedMultiInOut {

    private[this] val hIn     : InDMain   = InDMain (this, shape.in0)
    private[this] val hSize   : InIAux    = InIAux  (this, shape.in1)(max(2, _))
    private[this] val hMinLag : InIAux    = InIAux  (this, shape.in2)(max(1, _))
    private[this] val hMaxLag : InIAux    = InIAux  (this, shape.in3)(max(1, _))
    private[this] val hThresh : InDAux    = InDAux  (this, shape.in4)(max(0.0, _))
    private[this] val hOctCost: InDAux    = InDAux  (this, shape.in5)(max(0.0, _))
    private[this] val hNum    : InIAux    = InIAux  (this, shape.in6)(max(1, _))
    private[this] val hOut0   : OutDMain  = OutDMain(this, shape.out0)
    private[this] val hOut1   : OutDMain  = OutDMain(this, shape.out1)

    private[this] var acBuf       : Array[Double] = _
    private[this] var lagBuf      : Array[Double] = _
    private[this] var lagIBuf     : Array[Int   ] = _
    private[this] var strengthBuf : Array[Double] = _
    private[this] var acSize      : Int     = 0
    private[this] var minLag      : Int     = _
    private[this] var maxLag      : Int     = _
    private[this] var thresh      : Double  = _
    private[this] var octCost     : Double  = _
    private[this] var numPaths    : Int     = 0

    protected def mainInDone: Boolean =
      hIn.isDone

    protected def isHotIn(inlet: Inlet[_]): Boolean =
      inlet == hIn.inlet

    protected def outAvailable: Int =
      min(hOut0.available, hOut1.available)

    protected def mainInAvailable: Int =
      hIn.available

    protected def flushOut(): Boolean =
      hOut0.flush() & hOut1.flush() // careful to always call both

    override protected def stopped(): Unit = {
      super.stopped()
      acBuf       = null
      lagBuf      = null
      lagIBuf     = null
      strengthBuf = null
    }

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hSize   .hasNext &&
        hMinLag .hasNext &&
        hMaxLag .hasNext &&
        hThresh .hasNext &&
        hOctCost.hasNext &&
        hNum    .hasNext

      if (ok) {
        val oldSize   = acSize
        val _winSize  = hSize.next()
        if (_winSize != oldSize) {
          acBuf  = new Array[Double](_winSize)
          acSize = _winSize
        }
        minLag    = hMinLag.next()
        maxLag    = hMaxLag.next()
        thresh    = hThresh.next()
        octCost   = hOctCost.next()
        val oldN  = numPaths
        numPaths  = hNum.next()
        if (numPaths != oldN) {
          lagBuf      = new Array[Double](numPaths)
          lagIBuf     = new Array[Int   ](numPaths)
          strengthBuf = new Array[Double](numPaths)
        }
      }
      ok
    }

    protected def readWinSize : Long = acSize
    protected def writeWinSize: Long = numPaths

    protected def readIntoWindow(chunk: Int): Unit = {
      hIn.nextN(acBuf, readOff.toInt, chunk)
    }

    protected def writeFromWindow(chunk: Int): Unit = {
      val rOff = writeOff.toInt
      hOut0.nextN(lagBuf      , rOff, chunk)
      hOut1.nextN(strengthBuf , rOff, chunk)
    }

    protected def processWindow(): Unit = {
      val sz0     = readOff.toInt
      val _buf    = acBuf
      val _bufSz  = acSize
      if (sz0 < _bufSz) {
        Util.clear(_buf, sz0, _bufSz - sz0)
      }

      /*

        "The unvoiced candidate, which has a _local strength_ equal to

        R ≡ VoicingThreshold + max(0, 2 - (localAbsPeak / globalAbsPeak) / (SilenceThreshold / (1 + VoicingThreshold))
        "

        Note: we use ar[0] instead of (localAbsPeak / globalAbsPeak). From comparison between ar[0] with local-peak
        in speech, we find that the median ratio between the two is 1:18 (ar is more stable, while local-peak has
        more rare peaks). Thus we obtain:

        R = thresh + max(0, 2 - 18*ar[0] / (SilenceThreshold / (1 + thresh))

        Note that R is only calculated in the Viberti search. In this stage, we simply look at the intensities,
        which is `18*ar[0]` for unvoiced, or the height of the _normalized_ AC local maximum, for the pitch candidates.

       */

      val n = numPaths
      Util.clear(lagBuf     , 0, n)
      Util.clear(strengthBuf, 0, n)

      val _minLag = math.min(_bufSz - 1, minLag)
      val _maxLag = math.min(_bufSz - 1, maxLag)
      val _thresh = thresh

      var li  = _minLag
      var vp  = _buf(_minLag - 1)
      var vi  = _buf(_minLag)

      var pathsTaken  = 0

      while (li < _maxLag) {  // XXX TODO --- `li` runs further in Boersma's implementation
        val vn = _buf(li + 1)
        if (vi > _thresh && vp < vi && vn < vi) {
          /*

            "A first crude estimate of the pitch period would be τ_max ≈ m Δτ
              ... We can improve this by parabolic interpolation around m Δτ"

           */

          val num   = 0.5 * (vn - vp)
          val denom = 2.0 * vi - (vn + vp)
          val lag   = li + num / denom

          // XXX TODO `len` is smaller ? in Boersma's implementation
          val strength0 = sincInterp(_buf, len = _bufSz, x = lag, maxDepth = 30)
          val strength  = strength0 // math.min(1.0, strength0)

          val pathIdx = if (pathsTaken < n) {
            val res = pathsTaken
            pathsTaken += 1
            res
          } else {
            val strengthC = strength + octCost * math.log (_maxLag / lag)
            var weakest   = strengthC
            var res = -1
            var j = 0
            while (j < n) {
              val lagJ      = lagBuf      (j)
              val strengthJ = strengthBuf (j) + octCost * math.log(_maxLag / lagJ)
              if (strengthJ < weakest) {
                weakest = strengthJ
                res     = j
              }
              j += 1
            }
            res
          }

          if (pathIdx >= 0) {
            lagBuf      (pathIdx) = lag   // XXX TODO --- could eliminate the assignment, it will anyway be overwritten
            lagIBuf     (pathIdx) = li
            strengthBuf (pathIdx) = strength
          }
        }
        li += 1
        vp = vi
        vi = vn
      }

      // "Second pass: for extra precision, maximize sin(x)/x interpolation ('sinc')."
      var pi = 0
      while (pi < pathsTaken) {
        val (lagOut, strengthOut) =
          improveExtremum(_buf, len = _bufSz, ix = lagIBuf(pi), interpolation = 4, isMaximum = true)
        lagBuf      (pi) = lagOut
        strengthBuf (pi) = strengthOut // math.min(1.0, strengthOut)
        pi += 1
      }

//      // 'unvoiced'
//      // lagBuf(pathsTaken) = 0.0
//      strengthBuf(pathsTaken) = math.sqrt(intensity) // 18 * intensity // math.min(1.0, 18 * intensity)

//      n
    }

    // adapted from Praat's NUMinterpol.cpp
    // author: Paul Boersma
    // license: GPL v2+
    // source: https://github.com/praat/praat/blob/539a3212b662264d56feb1493ce2edbef721dbcf/melder/NUMinterpol.cpp
    private def sincInterp(xs: Array[Double], len: Int, x: Double, maxDepth: Int): Double = {
      import math.{Pi, cos, sin}
      val midLeft   = x.toInt
      val midRight  = midLeft + 1

      // ---- simple cases ----
      if      (x >= len) xs(len - 1)
      else if (x <   0) xs(0)
      else if (x == midLeft) xs(midLeft)
      else {
        // 1 < x < nx && x not integer: interpolate.

//        val maxDepthC = math.min(math.min(maxDepth, midRight - 1),  len - midLeft)
        val maxDepthC = math.min(math.min(maxDepth, midLeft),  len - midRight)

        if      (maxDepthC <= 0 /* nearest */) xs((x + 0.5).toInt)
        else if (maxDepthC == 1 /* linear  */) xs(midLeft) + (x - midLeft) * (xs(midRight) - xs(midLeft))
        else if (maxDepthC == 2 /* cubic   */) {
          val yl  = xs(midLeft)
          val yr  = xs(midRight)
          val dyl = 0.5 * (yr - xs(midLeft - 1))
          val dyr = 0.5 * (xs(midRight + 1) - yl)
          val fil = x - midLeft
          val fir = midRight - x
          yl * fir + yr * fil - fil * fir * (0.5 * (dyr - dyl) + (fil - 0.5) * (dyl + dyr - 2 * (yr - yl)))

        } else {  // full sinc algorithm
          var res = 0.0

          val left      = midRight - maxDepthC
          val right     = midLeft  + maxDepthC
          var a         = Pi * (x - midLeft)
          var halfSinA  = 0.5 * sin(a)
          var aa        = a  / (x - left + 1)
          var daa       = Pi / (x - left + 1)

          var ix = midLeft
          while (ix >= left) {
            val d    = halfSinA / a * (1.0 + cos(aa))
            res     += xs(ix) * d
            a       += Pi
            aa      += daa
            halfSinA = - halfSinA

            ix -= 1
          }

          a         = Pi * (midRight - x)
          halfSinA  = 0.5 * sin(a)
          aa        = a  / (right - x + 1)
          daa       = Pi / (right - x + 1)

          ix = midRight
          while (ix <= right) {
            val d    = halfSinA / a * (1.0 + cos(aa))
            res     += xs(ix) * d
            a       += Pi
            aa      += daa
            halfSinA = - halfSinA

            ix += 1
          }
          res
        }
      }
    }

    // adapted from Praat's NUMinterpol.cpp
    // author: Paul Boersma
    // license: GPL v2+
    // source: https://github.com/praat/praat/blob/539a3212b662264d56feb1493ce2edbef721dbcf/melder/NUMinterpol.cpp
    //
    // returns tuple (x = lag, y = strength)
    private def improveExtremum(xs: Array[Double], len: Int, ix: Int, interpolation: Int,
                                isMaximum: Boolean): (Double, Double) = {
      if (ix <= 0) {
        (0.0 , xs(0))
      } else if (ix >= len - 1) {
        ((len - 1).toDouble, xs(len - 1))
      } else if (interpolation <= 0 /* none */) {
        (ix.toDouble, xs(ix))
      } else if (interpolation == 1 /* parabolic */) {
        val num   = 0.5 * (xs(ix + 1) - xs(ix - 1))
        val denom = 2.0 * xs(ix) - (xs(ix + 1) + xs(ix - 1))
        val xOut  = ix + num / denom
        val yOut  = xs(ix) + 0.5 * num * num / denom
        (xOut, yOut)
      } else {
        /* Sinc interpolation. */
        val maxDepth = if (interpolation == 3 /* sinc70 */) 70 else 700
        val (xOut, yOut0) = minimizeBrent(a0 = ix - 1, b0 = ix + 1, tolerance = 1e-10) { x =>
          val y = sincInterp(xs, len = len, x = x, maxDepth = maxDepth)
          if (isMaximum) -y else y
        }
        val yOut = if (isMaximum) -yOut0 else yOut0
        (xOut, yOut)
      }
    }

    // adapted from Praat's NUMinterpol.cpp
    // author: Paul Boersma, David Weenink ("Closely modeled after the netlib code by Oleg Kiselyov.")
    // license: GPL v2+
    // source: https://github.com/praat/praat/blob/13954f1bb49d249ef827a097c9ba3b06d7a7899f/dwsys/NUM2.cpp
   private def minimizeBrent(a0: Double, b0: Double, tolerance: Double)
                            (f: Double => Double): (Double, Double) = {
      import math.abs
      val golden    = 0.3819660112501051
      val eps       = 1.0e-30 // XXX TODO sqrt(NUMfpp -> eps)
      val iterMax   = 60

      var a   = a0
      var b   = b0

      require (tolerance > 0 && a < b)

      /* First step - golden section */

      var v   = a + golden * (b - a)
      var fv  = f(v)
      var x   = v
      var w   = v
      var fx  = fv
      var fw  = fv

      var iter = 1
      while (iter <= iterMax) {
        val range     = b - a
        val midRange  = (a + b) / 2.0
        val tolAct    = eps * abs(x) + tolerance / 3.0

        if (abs(x - midRange) + range / 2.0 <= 2.0 * tolAct) {
          return (x, fx)
        }

        // Obtain the golden section step

        var newStep = golden * (if (x < midRange) b - x else a - x)

        // Decide if the parabolic interpolation can be tried

        if (abs(x - w) >= tolAct) {
          /*
            Interpolation step is calculated as p/q;
            division operation is delayed until last moment.
          */

          val t = (x - w) * (fx - fv)
          var q = (x - v) * (fx - fw)
          var p = (x - v) * q - (x - w) * t
          q = 2.0 * (q - t)

          if (q > 0.0) {
            p = -p
          } else {
            q = -q
          }

          /*
            If x+p/q falls in [a,b], not too close to a and b,
            and isn't too large, it is accepted.
            If p/q is too large then the golden section procedure can
            reduce [a,b] range.
          */

          if (abs(p) < abs(newStep * q) &&
            p > q * (a - x + 2.0 * tolAct) &&
            p < q * (b - x - 2.0 * tolAct)) {

            newStep = p / q
          }
        }

        // Adjust the step to be not less than tolerance.

        if (abs(newStep) < tolAct) {
          newStep = if (newStep > 0.0) tolAct else - tolAct
        }

        // Obtain the next approximation to min	and reduce the enveloping range

        {
          val t = x + newStep	// Tentative point for the min
          val ft = f(t)

          /*
            If t is a better approximation, reduce the range so that
            t would fall within it. If x remains the best, reduce the range
            so that x falls within it.
          */

          if (ft <= fx) {
            if (t < x) {
              b = x
            } else {
              a = x
            }

            v = w; w = x; x = t
            fv = fw; fw = fx; fx = ft
          } else {
            if (t < x) {
              a = t
            } else {
              b = t
            }

            if (ft <= fw || w == x) {
              v = w; w = t
              fv = fw; fw = ft
            } else if (ft <= fv || v == x || v == w) {
              v = t
              fv = ft
            }
          }
        }

        iter += 1
      }
      (x, fx)
    }
  }
}