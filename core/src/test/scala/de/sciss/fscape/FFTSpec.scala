package de.sciss.fscape

import de.sciss.fscape.stream.Control.Config
import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

class FFTSpec extends AnyFlatSpec with Matchers {
  "The real-valued FFT UGens" should "work as specified" in {
    val fftSizeHs = List(
      16, 512, 1024, 4096
    )

    for {
      fftSizeH  <- fftSizeHs
      full      <- Seq(false, true)
    } {
      val rmsInP    = Promise[Vec[Double]]()
      val rmsFreqP  = Promise[Vec[Double]]()
      val rmsOutP   = Promise[Vec[Double]]()
      val magP      = Promise[Vec[Double]]()
      val phaseP    = Promise[Vec[Double]]()
      val sigOutP   = Promise[Vec[Double]]()
      val rmsExp    = math.sqrt(0.5)
      val eps       = 1.0e-5
      val fftSize   = fftSizeH << 1
      val period    = fftSize / 2
      val phase0    = math.Pi/2

      val g = Graph {
        import graph._

        val sig  = SinOsc(1.0 / period, phase = phase0).take(fftSize)

        def rms(sig: GE, p: Promise[Vec[Double]]): Unit = {
          val v = (sig.squared.sum / fftSize).sqrt // .ampDb
          DebugDoublePromise(v, p)
        }

        rms(sig, rmsInP)

        val (fft, magS) = if (full) {
          val _fft    = Real1FullFFT(sig, fftSize)
          val _mag    = _fft.complex.mag * math.sqrt(fftSize) * 0.5
          (_fft, _mag)
        } else {
          val _fft    = Real1FFT(sig, fftSize, mode = 1)
          val _mag    = _fft.complex.mag * math.sqrt(fftSizeH)
          (_fft, _mag)
        }

        rms(magS, rmsFreqP)
        val mag   = fft.complex.mag
        val phase = fft.complex.phase

        val ifft = if (full) {
          Real1FullIFFT(fft, fftSize)
        } else {
          Real1IFFT(fft, fftSize, mode = 1)
        }

//        Plot1D(ifft, fftSize)
        rms(ifft, rmsOutP)

        DebugDoublePromise(ifft , sigOutP )
        DebugDoublePromise(mag  , magP    )
        DebugDoublePromise(phase, phaseP  )
      }

      val cfg = Config()
      cfg.blockSize = 1024
      val ctl = stream.Control(cfg)
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)
//      Thread.sleep(10000)

      def getPromiseVec(in: Promise[Vec[Double]]): Vec[Double] =
        in.future.value.get.get

      def getPromise(in: Promise[Vec[Double]]): Double = {
        val sq = getPromiseVec(in)
        assert (sq.size === 1)
        sq.head
      }

      val resRmsIn  : Double = getPromise(rmsInP  )
      val resRmsFreq: Double = getPromise(rmsFreqP)
      val resRmsOut : Double = getPromise(rmsOutP )
      assert (resRmsIn    === (rmsExp +- eps))
      assert (resRmsFreq  === (rmsExp +- eps))
      assert (resRmsOut   === (rmsExp +- eps))

      val resOut  = getPromiseVec(sigOutP)
      val pi2     = math.Pi * 2
      val expOut  = Vector.tabulate(fftSize)(idx => math.sin(idx * pi2 / period + phase0))

      val resMag  = getPromiseVec(magP)
      val expMag  = Vector.tabulate(if (full) fftSize else fftSizeH + 1) { idx =>
        if (idx == 2 || idx == fftSize - 2) 1.0 else 0.0
      }
      val resPhaseSq  = getPromiseVec(phaseP)
      val resPhase    = resPhaseSq(2)

//      println(resMag.mkString("GOT: ", ", ", ""))
//      println(expOut.mkString("EXP: ", ", ", ""))

      def difOk(a: Vec[Double], b: Vec[Double]): Unit = {
        assert (a.size === b.size)
        (a zip b).zipWithIndex.foreach { case ((av, bv), idx) =>
          assert (av === bv +- eps, s"For fftSize $fftSize, full? $full, idx $idx")
        }
      }

      difOk(resOut, expOut)
      difOk(resMag, expMag)

      assert (resPhase === (0.0 +- eps))
    }
  }

  they should "allow fft-size modulation" in {
    val fftSizes = Vector(
      16, 32, 48, 57, 511, 512, 513, 1023, 1024, 1025
    )

    val sigLen    = fftSizes.sum
    val phaseP    = Promise[Vec[Double]]()
    val sigOutP   = Promise[Vec[Double]]()
    val eps       = 1.0e-5

    val g = Graph {
      import graph._

      val fftSizesGE: GE = fftSizes.map(x => x: GE).reduce(_ ++ _)
      val sig     = DelayN(Metro(fftSizesGE).take(sigLen - 1), 1, 1)
      val fft     = Real1FFT(sig, size = fftSizesGE, mode = 1)
      val phase   = fft.complex.phase
      val ifft    = Real1IFFT(fft, size = fftSizesGE, mode = 1)

      DebugDoublePromise(ifft , sigOutP )
      DebugDoublePromise(phase, phaseP  )
    }

    val cfg = Config()
    cfg.blockSize = 1024
    val ctl = stream.Control(cfg)
    ctl.run(g)
    Await.result(ctl.status, Duration.Inf)
    //      Thread.sleep(10000)

    def getPromiseVec(in: Promise[Vec[Double]]): Vec[Double] =
      in.future.value.get.get

    def getPromise(in: Promise[Vec[Double]]): Double = {
      val sq = getPromiseVec(in)
      assert (sq.size === 1)
      sq.head
    }

    val resOut  = getPromiseVec(sigOutP)
    val expOut  = (0.0 +: fftSizes.flatMap(n => Vector.tabulate(n)(i => if (i == 0) 1.0 else 0.0))).init

    val resPhaseSq  = getPromiseVec(phaseP)
    val resPhase    = resPhaseSq(2)

    def difOk(obs: Vec[Double], exp: Vec[Double]): Unit = {
      assert (obs.size === exp.size)
      (obs zip exp).zipWithIndex.foreach { case ((obsV, expV), idx) =>
        assert (obsV === expV +- eps, s"For idx $idx of ${obs.size}")
      }
    }

    assert (resOut.size === sigLen)

    difOk(resOut, expOut)
//    difOk(resMag, expMag)

//    assert (resPhase === (0.0 +- eps))
  }
}