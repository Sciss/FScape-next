package de.sciss.fscape
package stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape, Outlet, OverflowStrategy}
import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object StreamTest extends App {
  // showStreamLog = true

  // val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
  val fIn   = userHome / "Documents" / "projects" / "Unlike" / "audio_work" / "mentasm-e8646341-63dcf8a8.aif"

  val fIn2  = userHome / "Music" / "work" / "_killme5.aif"
  // val fIn   = userHome / "Music" / "work" / "fft_test.aif"
  //  val fIn   = userHome / "Music" / "work" / "B19h39m45s23jan2015.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"
  val fOut2 = userHome / "Music" / "work" / "_killme2.aif"
  val fOut3 = userHome / "Music" / "work" / "_killme3.aif"

  val blockSize = 1024
  val config = Control.Config()
  config.blockSize = blockSize
  implicit val system = ActorSystem()
  config.materializer = ActorMaterializer(
    ActorMaterializerSettings(system)
    //      .withInputBuffer(
    //        initialSize = 1024,
    //        maxSize     = 1024)
  )
  implicit val ctrl = Control(config)

  lazy val graphFFT = GraphDSL.create() { implicit dsl =>
    implicit val b = Builder()

    val in      = AudioFileIn(file = fIn, numChannels = 1).head
    val size    = b.add(Source.single(BufI(65536))).out
    val padding = b.add(Source.single(BufI(    0))).out
    val mode    = b.add(Source.single(BufI(    0))).out
    val fft     = Real1FFT(in, size = size, padding = padding, mode = mode)
    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft :: Nil)
    ClosedShape
  }

  lazy val graphWin = GraphDSL.create() { implicit dsl =>
    implicit val b = Builder()

    val in      = AudioFileIn(file = fIn, numChannels = 1).head
    val fftSize = 32768 // 8192
    val winIn   = GenWindow(size = const(fftSize), shape = const(graph.GenWindow.Hann.id), param = const(0.0))
    val winOut  = BinaryOp(in1 = in, in2 = winIn, op = graph.BinaryOp.Times)
    val sig     = winOut
    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig :: Nil)
    ClosedShape
  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val size    = b.add(Source.single(BufI(500))).out
//    val step    = b.add(Source.single(BufI(447/2))).out
//    val slid    = Sliding(in, size = size, step = step)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = slid)
//    ClosedShape
//  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val size1   = b.add(Source.single(BufI(1024))).out
//    val padding1= b.add(Source.single(BufI(   0))).out
//    val size2   = b.add(Source.single(BufI(1024))).out
//    val padding2= b.add(Source.single(BufI(   0))).out
//    //    val fft     = Real1FFT (in = in , size = size1, padding = padding1)
//    //    val ifft    = Real1IFFT(in = fft, size = size2, padding = padding2)
//    val fft     = Real1FullFFT (in = in , size = size1, padding = padding1)
//    val ifft    = Real1FullIFFT(in = fft, size = size2, padding = padding2)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = ifft)
//    ClosedShape
//  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val size    = b.add(Source.single(BufI(1024))).out
//    val clump   = b.add(Source.single(BufI(   2))).out
//    val sig     = ReverseWindow(in = in, size = size, clump = clump)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
//    ClosedShape
//  }

  def const(i: Int   )(implicit b: GraphDSL.Builder[NotUsed]): Outlet[BufI] = b.add(Source.single(BufI(i))).out
  def const(n: Long  )(implicit b: GraphDSL.Builder[NotUsed]): Outlet[BufL] = b.add(Source.single(BufL(n))).out
  def const(d: Double)(implicit b: GraphDSL.Builder[NotUsed]): Outlet[BufD] = b.add(Source.single(BufD(d))).out

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val seq0    = UnzipWindowN(numOutputs = 3, in = in, size = const(100))
//    import GraphDSL.Implicits._
//    val seq     = seq0 // .map(_.buffer(3, OverflowStrategy.backpressure).outlet)
//    val sig     = ZipWindowN(in = seq, size = const(100))
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
//    ClosedShape
//  }

  lazy val graphOLD = GraphDSL.create() { implicit dsl =>
    implicit val b = Builder()

    // 'analysis'
//    val in          = DiskIn(file = fIn)
    val in          = AudioFileIn(file = fIn2, numChannels = 1).head
//    val fftSize     = 131072
    val fftSize     = 32768 // 8192
    val winStep     = fftSize // / 4
    val inW         = Sliding       (in = in  , size = const(fftSize), step    = const(winStep))
    val fft         = Real1FullFFT  (in = inW , size = const(fftSize), padding = const(0))

    // 'percussion'
    val log         = ComplexUnaryOp(in = fft , op = graph.ComplexUnaryOp.Log)
    val logC        = BinaryOp      (in1 = log , in2 = const(-56 /* -80 */), op = graph.BinaryOp.Max)
    val cep0        = Complex1IFFT  (in  = logC, size = const(fftSize), padding = const(0))
    val cep         = BinaryOp      (in1 = cep0 , in2 = const(1.0/fftSize), op = graph.BinaryOp.Times)
    val (pos0, neg0) = UnzipWindow   (in = cep , size = const(fftSize))
    import GraphDSL.Implicits._
    val pos1        = ResizeWindow  (in = pos0, size = const(fftSize), start = const(0), stop = const(2)) // 'add nyquist'
    val neg1        = ResizeWindow  (in = neg0, size = const(fftSize), start = const(0), stop = const(2)) // 'add dc'

    val pos2        = pos1.buffer(size = (fftSize + 2)/blockSize, overflowStrategy = OverflowStrategy.backpressure).outlet
    val negR2       = ReverseWindow (in = neg1 , size = const(fftSize + 2), clump = const(2))

    // val posB = BroadcastBuf(pos2, 2)
    val pos  = pos2 // posB(0)

    // val negRB = BroadcastBuf(negR2, 2)
    val negR = negR2 // negRB(0)

//    DiskOut(file = fOut2, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = posB (1))
//    DiskOut(file = fOut3, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = negRB(1))

    /*

    positive window: A = Re, B = im
    negative window: C = Re, D = im  (reversed order, i.e. from low to high frequencies)

    A' = A * crr + C * ccr
    C' = C * clr + A * car
    B' = B * cri + D * cci
    D' = D * cli + B * cai

     */

    val (aIn, bIn)  = UnzipWindow   (in = pos , size = const(1))
    val (cIn, dIn)  = UnzipWindow   (in = negR, size = const(1))

    // 'variant 1'
//    val crr =  0; val cri =  0
//    val clr = +1; val cli = +1
//    val ccr = +1; val cci = -1
//    val car = +1; val cai = -1

    // 'bypass'
//    val crr = +1; val cri = +1
//    val clr = +1; val cli = +1
//    val ccr =  0; val cci =  0
//    val car =  0; val cai =  0

    // 'variant 2'
    val crr = +1; val cri = +1
    val clr =  0; val cli =  0
    val ccr = +1; val cci = -1
    val car = +1; val cai = -1

    val aInB        = BroadcastBuf(aIn, 2)
    val bInB        = BroadcastBuf(bIn, 2)
    val cInB        = BroadcastBuf(cIn, 2)
    val dInB        = BroadcastBuf(dIn, 2)

    import graph.BinaryOp.{Plus, Times}
    val am1         = BinaryOp(op = Times, in1 = aInB(0), in2 = const(crr))
    val am2         = BinaryOp(op = Times, in1 = cInB(0), in2 = const(ccr))
    val aOut        = BinaryOp(op = Plus , in1 = am1, in2 = am2)

    val bm1         = BinaryOp(op = Times, in1 = bInB(0), in2 = const(cri))
    val bm2         = BinaryOp(op = Times, in1 = dInB(0), in2 = const(cci))
    val bOut        = BinaryOp(op = Plus , in1 = bm1, in2 = bm2)

    val cm1         = BinaryOp(op = Times, in1 = cInB(1), in2 = const(clr))
    val cm2         = BinaryOp(op = Times, in1 = aInB(1), in2 = const(car))
    val cOut        = BinaryOp(op = Plus , in1 = cm1, in2 = cm2)

    val dm1         = BinaryOp(op = Times, in1 = dInB(1), in2 = const(cli))
    val dm2         = BinaryOp(op = Times, in1 = bInB(1), in2 = const(cai))
    val dOut        = BinaryOp(op = Plus , in1 = dm1, in2 = dm2)

    val posOut0     = ZipWindow(a = aOut, b = bOut, size = const(1))
    val negOutR0    = ZipWindow(a = cOut, b = dOut, size = const(1))

    val posOut1     = ResizeWindow(in = posOut0 , size = const(fftSize + 2), start = const(0), stop = const(-2))
    // here `start` because we do this before reversal
    val negOutR1    = ResizeWindow(in = negOutR0, size = const(fftSize + 2), start = const(2), stop = const( 0))

    val posOut      = posOut1.buffer(size = fftSize/blockSize, overflowStrategy = OverflowStrategy.backpressure).outlet
    val negOutR     = negOutR1 // .buffer(size = fftSize/blockSize, overflowStrategy = OverflowStrategy.backpressure).outlet
    val negOut      = ReverseWindow (in = negOutR, size = const(fftSize), clump = const(2))
    val logOut      = ZipWindow(a = posOut, b = negOut, size = const(fftSize))
    val freq0       = Complex1FFT   (in = logOut, size = const(fftSize), padding = const(0))
    val freq        = BinaryOp      (in1 = freq0 , in2 = const(fftSize), op = Times)
    val fftOut      = ComplexUnaryOp(in = freq  , op = graph.ComplexUnaryOp.Exp)

    // 'synthesis'
    val outW        = Real1FullIFFT (in = fftOut, size = const(fftSize), padding = const(0))
    val sig         = outW  // XXX TODO: apply window function and overlap-add
    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig :: Nil)
    ClosedShape
  }

  lazy val graphMONO = GraphDSL.create() { implicit dsl =>
    implicit val b = Builder()

    import graph.BinaryOp.{Max, Times}

    // 'analysis'
    val in          = AudioFileIn(file = fIn, numChannels = 1).head
    val fftSize     = 131072 // 32768 // 8192
    val winStep     = fftSize / 4
    val inW         = Sliding       (in = in  , size = const(fftSize), step    = const(winStep))
    val fft0        = Real1FullFFT  (in = inW , size = const(fftSize), padding = const(0))
    val fft         = fft0 // ComplexUnaryOp(in = fft0, op = ComplexUnaryOp.Conj)

    // 'percussion'
    val log         = ComplexUnaryOp(in = fft , op = graph.ComplexUnaryOp.Log)
    val logC        = BinaryOp      (in1 = log , in2  = const(-80), op = Max)
    val cep0        = Complex1IFFT  (in  = logC, size = const(fftSize), padding = const(0))
    val cep         = BinaryOp      (in1 = cep0, in2  = const(1.0/fftSize), op = Times)

    // 'variant 1'
    val crr =  0; val cri =  0
    val clr = +1; val cli = +1
    val ccr = +1; val cci = -1
    val car = +1; val cai = -1

    // 'bypass'
    //    val crr = +1; val cri = +1
    //    val clr = +1; val cli = +1
    //    val ccr =  0; val cci =  0
    //    val car =  0; val cai =  0

    // 'variant 2'
    //    val crr = +1; val cri = +1
    //    val clr =  0; val cli =  0
    //    val ccr = +1; val cci = -1
    //    val car = +1; val cai = -1

    val cepOut      = FoldCepstrum  (in = cep, size = const(fftSize),
      crr = const(crr), cri = const(cri), clr = const(clr), cli = const(cli),
      ccr = const(ccr), cci = const(cci), car = const(car), cai = const(cai))
    val freq0       = Complex1FFT   (in = cepOut, size = const(fftSize), padding = const(0))
    val freq1       = BinaryOp      (in1 = freq0, in2 = const(fftSize), op = Times)
    val freq        = freq1 // ComplexUnaryOp(in = freq1, op = ComplexUnaryOp.Conj)
    val fftOut      = ComplexUnaryOp(in = freq  , op = graph.ComplexUnaryOp.Exp)

    // 'synthesis'
    val outW        = Real1FullIFFT (in = fftOut, size = const(fftSize), padding = const(0))
    val sig0        = outW  // XXX TODO: apply window function and overlap-add

    // XXX TODO --- what's this gain factor?
    val gain        = BinaryOp      (in1 = sig0, in2 = const(1.0/2097152), op = Times)
    val winIn       = GenWindow(size = const(fftSize), shape = const(graph.GenWindow.Hann.id), param = const(0.0))
    val winOut      = BinaryOp(in1 = gain, in2 = winIn, op = Times)
    val sig         = OverlapAdd(winOut, size = const(fftSize), step = const(winStep))

    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig :: Nil)
    ClosedShape
  }

  lazy val _graph = GraphDSL.create() { implicit dsl =>
    implicit val b = Builder()

    import graph.BinaryOp.{Max, Times}

    // 'analysis'
    val in          = AudioFileIn(file = fIn, numChannels = 1).head
    val fftSize     = 131072 // 32768 // 8192
    val winStep     = fftSize / 4
    val inW         = Sliding       (in = in  , size = const(fftSize), step    = const(winStep))
    val fft0        = Real1FullFFT  (in = inW , size = const(fftSize), padding = const(0))
    val fft         = fft0 // ComplexUnaryOp(in = fft0, op = ComplexUnaryOp.Conj)

    // 'percussion'
    val log         = ComplexUnaryOp(in = fft , op = graph.ComplexUnaryOp.Log)
    val logC        = BinaryOp      (in1 = log , in2  = const(-80), op = Max)
    val cep0        = Complex1IFFT  (in  = logC, size = const(fftSize), padding = const(0))
    val cep1        = BinaryOp      (in1 = cep0, in2  = const(1.0/fftSize), op = Times)

    val coefs       = Vector(CepCoef.One, CepCoef.Two)
    val cepB        = BroadcastBuf(in = cep1, numOutputs = 2)
    val sig         = (coefs zip cepB).map { case (coef, cep) =>
      import coef._
      val cepOut      = FoldCepstrum  (in = cep, size = const(fftSize),
        crr = const(crr), cri = const(cri), clr = const(clr), cli = const(cli),
        ccr = const(ccr), cci = const(cci), car = const(car), cai = const(cai))
      val freq0       = Complex1FFT   (in = cepOut, size = const(fftSize), padding = const(0))
      val freq1       = BinaryOp      (in1 = freq0, in2 = const(fftSize), op = Times)
      val freq        = freq1 // ComplexUnaryOp(in = freq1, op = ComplexUnaryOp.Conj)
      val fftOut      = ComplexUnaryOp(in = freq  , op = graph.ComplexUnaryOp.Exp)

      // 'synthesis'
      val outW        = Real1FullIFFT(in = fftOut, size = const(fftSize), padding = const(0))

      val times       = BinaryOp(in1 = outW, in2 = const(gain), op = Times)
      val winIn       = GenWindow(size = const(fftSize), shape = const(graph.GenWindow.Hann.id), param = const(0.0))
      val winOut      = BinaryOp(in1 = times, in2 = winIn, op = Times)
      val lap         = OverlapAdd(winOut, size = const(fftSize), step = const(winStep))
      lap
    }

    AudioFileOut(file = fOut, spec = AudioFileSpec(numChannels = sig.size, sampleRate = 44100), in = sig)
    ClosedShape
  }

  //  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val sig     = ResizeWindow(in = in, size = const(1000), start = const(-200), stop = const(200))
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
//    ClosedShape
//  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val fftSize = 4096
//    val fft     = Complex1FFT (in = in , size = const(fftSize), padding = const(0))
//    val sig     = Complex1IFFT(in = fft, size = const(fftSize), padding = const(0))
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
//    ClosedShape
//  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val size    = b.add(Source.single(BufI(2 /* 1024 */))).out
//    val (sig1, sig2) = UnzipWindow(in = in, size = size)
//    DiskOut(file = fOut , spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig1)
//    DiskOut(file = fOut2, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig2)
//    ClosedShape
//  }

//  val graph = GraphDSL.create() { implicit b =>
//    val in  = Source.unfoldResource[Double, Iterator[Int]](
//      () => Iterator(1 to 10: _*), it => if (it.hasNext) Some(it.next().toDouble) else None, _ => ())
//    import GraphDSL.Implicits._
//    val fft = in.importAndGetPort(b) // Real1FFT(in, size = 1024)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft)
//    ClosedShape
//  }

//  val graph1 = GraphDSL.create() { implicit b =>
//    val in  = Source.fromIterator(() => (1 to 10).iterator.map(_.toDouble))
//    val out = Sink.foreach[Double] { d =>
//      println(s"elem: $d")
//    }
//    import GraphDSL.Implicits._
//    val inOutlet = b.add(in).out
//    inOutlet ~> out
//    ClosedShape
//  }

  val rg  = RunnableGraph.fromGraph(_graph)
  val res = rg.run()(config.materializer)

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}