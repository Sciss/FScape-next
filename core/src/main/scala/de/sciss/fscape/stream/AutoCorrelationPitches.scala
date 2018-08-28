/*
 *  AutoCorrelationPitches.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, Outlet}
import akka.stream.stage.OutHandler
import de.sciss.fscape.stream.impl.{AuxInHandlerImpl, FilterLogicImpl, FullInOutImpl, In7Out2Shape, NodeImpl, ProcessInHandlerImpl, StageImpl, WindowedLogicImpl}

object AutoCorrelationPitches {
  def apply(ac: OutD, size: OutI, minLag: OutI, maxLag: OutI, thresh: OutD, octaveCost: OutD, n: OutI)
           (implicit b: Builder): (OutD, OutD) = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(ac        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(minLag    , stage.in2)
    b.connect(maxLag    , stage.in3)
    b.connect(thresh    , stage.in4)
    b.connect(octaveCost, stage.in5)
    b.connect(n         , stage.in6)

    (stage.out0, stage.out1)
  }

  private final val name = "AutoCorrelationPitches"

  private type Shape = In7Out2Shape[BufD, BufI, BufI, BufI, BufD, BufD, BufI,   BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = In7Out2Shape(
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

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FullInOutImpl[Shape]
      with FilterLogicImpl[BufD, Shape] {

    private[this] var acBuf       : Array[Double] = _
    private[this] var lagBuf      : Array[Double] = _
    private[this] var lagIBuf     : Array[Int   ] = _
    private[this] var strengthBuf : Array[Double] = _
    private[this] var acSize      : Int     = 0
    private[this] var minLag      : Int     = _
    private[this] var maxLag      : Int     = _
    private[this] var thresh      : Double  = _
    private[this] var octaveCost  : Double  = _
    private[this] var numPaths    : Int     = 0

    protected     var bufIn0 : BufD  = _
    private[this] var bufIn1 : BufI  = _
    private[this] var bufIn2 : BufI  = _
    private[this] var bufIn3 : BufI  = _
    private[this] var bufIn4 : BufD  = _
    private[this] var bufIn5 : BufD  = _
    private[this] var bufIn6 : BufI  = _
    private[this] var bufOut0: BufD = _
    private[this] var bufOut1: BufD = _

    protected def in0: InD = shape.in0

    private[this] var _canRead  = false
    private[this] var _inValid  = false
    private[this] var _canWrite = false

    def canRead : Boolean = _canRead
    def inValid : Boolean = _inValid
    def canWrite: Boolean = _canWrite

    private final class OutHandlerImpl[A](out: Outlet[A])
      extends OutHandler {

      def onPull(): Unit = {
        logStream(s"onPull($out)")
        updateCanWrite()
        if (canWrite) process()
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish($out)")
        val allClosed = shape.outlets.forall(isClosed(_))
        if (allClosed) super.onDownstreamFinish()
      }

      setOutHandler(out, this)
    }

    new ProcessInHandlerImpl (shape.in0, this)
    new AuxInHandlerImpl     (shape.in1, this)
    new AuxInHandlerImpl     (shape.in2, this)
    new AuxInHandlerImpl     (shape.in3, this)
    new AuxInHandlerImpl     (shape.in4, this)
    new AuxInHandlerImpl     (shape.in5, this)
    new AuxInHandlerImpl     (shape.in6, this)
    new OutHandlerImpl       (shape.out0)
    new OutHandlerImpl       (shape.out1)

    def updateCanWrite(): Unit = {
      val sh = shape
      _canWrite =
        (isClosed(sh.out0) || isAvailable(sh.out0)) &&
        (isClosed(sh.out1) || isAvailable(sh.out1))
    }

    protected def writeOuts(off: Int): Unit = {
      if (off > 0 && isAvailable(shape.out0)) {
        bufOut0.size = off
        push(shape.out0, bufOut0)
      } else {
        bufOut0.release()
      }
      if (off > 0 && isAvailable(shape.out1)) {
        bufOut1.size = off
        push(shape.out1, bufOut1)
      } else {
        bufOut1.release()
      }
      bufOut0   = null
      bufOut1   = null
      _canWrite = false
    }

    protected def allocOutputBuffers(): Int = {
      bufOut0 = ctrl.borrowBufD()
      bufOut1 = ctrl.borrowBufD()
      bufOut0.size
    }

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
      pull(sh.in3)
      pull(sh.in4)
      pull(sh.in5)
      pull(sh.in6)
    }

    override protected def stopped(): Unit = {
      acBuf       = null
      lagBuf      = null
      lagIBuf     = null
      strengthBuf = null
      freeInputBuffers()
      freeOutputBuffers()
    }

    protected def readIns(): Int = {
      freeInputBuffers()
      val sh    = shape
      bufIn0    = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      if (isAvailable(sh.in1)) {
        bufIn1 = grab(sh.in1)
        tryPull(sh.in1)
      }
      if (isAvailable(sh.in2)) {
        bufIn2 = grab(sh.in2)
        tryPull(sh.in2)
      }
      if (isAvailable(sh.in3)) {
        bufIn3 = grab(sh.in3)
        tryPull(sh.in3)
      }
      if (isAvailable(sh.in4)) {
        bufIn4 = grab(sh.in4)
        tryPull(sh.in4)
      }
      if (isAvailable(sh.in5)) {
        bufIn5 = grab(sh.in5)
        tryPull(sh.in5)
      }
      if (isAvailable(sh.in6)) {
        bufIn6 = grab(sh.in6)
        tryPull(sh.in6)
      }

      _inValid = true
      _canRead = false
      bufIn0.size
    }

    protected def freeInputBuffers(): Unit = {
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
      if (bufIn2 != null) {
        bufIn2.release()
        bufIn2 = null
      }
      if (bufIn3 != null) {
        bufIn3.release()
        bufIn3 = null
      }
      if (bufIn4 != null) {
        bufIn4.release()
        bufIn4 = null
      }
      if (bufIn5 != null) {
        bufIn5.release()
        bufIn5 = null
      }
      if (bufIn6 != null) {
        bufIn6.release()
        bufIn6 = null
      }
    }

    protected def freeOutputBuffers(): Unit = {
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }
      if (bufOut1 != null) {
        bufOut1.release()
        bufOut1 = null
      }
    }

    def updateCanRead(): Unit = {
      val sh = shape
      _canRead = isAvailable(sh.in0) &&
        ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3)) &&
        ((isClosed(sh.in4) && _inValid) || isAvailable(sh.in4))
    }

    protected def startNextWindow(inOff: Int): Long = {
      // size: OutI, minLag: OutI, maxLag: OutI, thresh: OutD, octaveCost: OutD, n:

      if (bufIn1 != null && inOff < bufIn1.size) {
        val oldSize   = acSize
        val _winSize  = math.max(2, bufIn1.buf(inOff))
        if (_winSize != oldSize) {
          acBuf  = new Array[Double](_winSize)
          acSize = _winSize
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        minLag = math.max(1, bufIn2.buf(inOff))
        // println(s"minLag $minLag")
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        maxLag = math.max(1, bufIn3.buf(inOff))
        // println(s"maxLag $maxLag")
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        thresh = math.max(0.0, bufIn4.buf(inOff)) * 0.5
        // println(s"thresh ${thresh * 2}")
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        octaveCost = math.max(0.0, bufIn5.buf(inOff)) / Util.log2
        // println(s"octaveCost ${octaveCost * Util.log2}")
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        val oldN = numPaths
        numPaths = math.max(2, bufIn6.buf(inOff))
        if (numPaths != oldN) {
          lagBuf      = new Array[Double](numPaths)
          lagIBuf     = new Array[Int   ](numPaths)
          strengthBuf = new Array[Double](numPaths)
        }
      }
      acSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, acBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val rOff = readFromWinOff.toInt
      Util.copy(lagBuf      , rOff, bufOut0.buf, outOff, chunk)
      Util.copy(strengthBuf , rOff, bufOut1.buf, outOff, chunk)
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val sz0     = writeToWinOff.toInt
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

      val intensity = _buf(0)   // XXX TODO --- this is obviously different from Boersma's implementatino
      if (intensity == 0) return n

      val gain = 1.0 / intensity
      // normalize AC
      var ni = 0
      while (ni < sz0) {
        _buf(ni) *= gain
        ni += 1
      }

      val _minLag = math.min(_bufSz - 1, minLag)
      val _maxLag = math.min(_bufSz - 1, maxLag)
      val _thresh = thresh

      var li  = _minLag
      var vp  = _buf(_minLag - 1)
      var vi  = _buf(_minLag)

      // val brent_ixmax = ifloor(nsamp_window * interpolation_depth)

      val n1          = n -1
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

          val pathIdx = if (pathsTaken < n1) {
            val res = pathsTaken
            pathsTaken += 1
            res
          } else {
            val strengthC = strength + octaveCost * math.log (_maxLag / lag)
            var weakest   = strengthC
            var res = -1
            var j = 0
            while (j < n1) {
              val lagJ      = lagBuf      (j)
              val strengthJ = strengthBuf (j) + octaveCost * math.log(_maxLag / lagJ)
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

      // 'unvoiced'
      // lagBuf(pathsTaken) = 0.0
      strengthBuf(pathsTaken) = 18 * intensity // math.min(1.0, 18 * intensity)

      n
    }

    // adapted from Praat's NUMinterpol.cpp
    // author: Paul Boersma
    // license: GPL v2+
    // source: https://github.com/praat/praat/blob/539a3212b662264d56feb1493ce2edbef721dbcf/melder/NUMinterpol.cpp
    private def sincInterp(xs: Array[Double], len: Int, x: Double, maxDepth: Int): Double = {
      import math.{Pi, sin, cos}
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