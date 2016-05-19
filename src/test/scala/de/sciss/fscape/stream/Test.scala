package de.sciss.fscape.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape, Outlet, OverflowStrategy}
import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.ExecutionContext
import scala.swing.Swing

object Test extends App {
  showStreamLog = true

  val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
  // val fIn   = userHome / "Music" / "work" / "fft_test.aif"
  //  val fIn   = userHome / "Music" / "work" / "B19h39m45s23jan2015.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"
  val fOut2 = userHome / "Music" / "work" / "_killme2.aif"

  import ExecutionContext.Implicits.global
  val blockSize = 1024
  implicit val ctrl = Control(blockSize)

//  val graph = GraphDSL.create() { implicit b =>
//    val in      = DiskIn(file = fIn)
//    val size    = b.add(Source.single(BufI(65536))).out
//    val padding = b.add(Source.single(BufI(    0))).out
//    val fft     = Real1FFT(in, size = size, padding = padding)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft)
//    ClosedShape
//  }

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
  def const(d: Double)(implicit b: GraphDSL.Builder[NotUsed]): Outlet[BufD] = b.add(Source.single(BufD(d))).out

  val graph = GraphDSL.create() { implicit b =>
    // 'analysis'
    val in          = DiskIn(file = fIn)
    val fftSize     = 131072
    val winStep     = fftSize / 4
    val inW         = Sliding       (in = in  , size = const(fftSize), step    = const(winStep))
    val fft         = Real1FullFFT  (in = inW , size = const(fftSize), padding = const(0))

    // 'percussion'
    val log         = ComplexUnaryOp(in = fft , op = ComplexUnaryOp.Log)
    val logC        = BinaryOp      (a  = log , b = const(-80), op = BinaryOp.Max)
    val cep         = Complex1IFFT  (in = logC, size = const(fftSize), padding = const(0))
    val (pos0, neg) = UnzipWindow   (in = cep , size = const(fftSize))
    import GraphDSL.Implicits._
    val pos         = pos0.buffer(size = fftSize/blockSize, overflowStrategy = OverflowStrategy.backpressure).outlet
    val negR        = ReverseWindow (in = neg , size = const(fftSize), clump   = const(2))

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

    val crr =  0; val cri =  0
    val clr = +1; val cli = +1
    val ccr = +1; val cci = -1
    val car = +1; val cai = -1

    val am1         = BinaryOp(op = BinaryOp.Times, a = aIn, b = const(crr))
    val am2         = BinaryOp(op = BinaryOp.Times, a = cIn, b = const(ccr))
    val aOut        = BinaryOp(op = BinaryOp.Plus , a = am1, b = am2)

    val bm1         = BinaryOp(op = BinaryOp.Times, a = bIn, b = const(cri))
    val bm2         = BinaryOp(op = BinaryOp.Times, a = dIn, b = const(cci))
    val bOut        = BinaryOp(op = BinaryOp.Plus , a = bm1, b = bm2)

    val cm1         = BinaryOp(op = BinaryOp.Times, a = cIn, b = const(clr))
    val cm2         = BinaryOp(op = BinaryOp.Times, a = aIn, b = const(car))
    val cOut        = BinaryOp(op = BinaryOp.Plus , a = cm1, b = cm2)

    val posOut      = ??? : Outlet[BufD]  // ZipWindow(size = const(1))
    val negOutR     = ??? : Outlet[BufD]  // ZipWindow(size = const(1))
    val negOut      = ReverseWindow (in = negOutR, size = const(fftSize), clump = const(2))
    val logOut      = ??? : Outlet[BufD]  // ZipWindow(size = const(fftSize))
    val fftOut      = ComplexUnaryOp(in = logOut, op = ComplexUnaryOp.Exp)
    val outW        = Real1FullIFFT(in = fftOut, size = const(fftSize), padding = const(0))

    // 'synthesis'
    val sig         = outW  // XXX TODO: apply window function and overlap-add
    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = sig)
    ClosedShape
  }

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

  implicit val system = ActorSystem()
  implicit val mat    = ActorMaterializer(
    ActorMaterializerSettings(system)
//      .withInputBuffer(
//        initialSize = 1024,
//        maxSize     = 1024)
  )

  val rg  = RunnableGraph.fromGraph(graph)
  val res = rg.run()

  Swing.onEDT {
    SimpleGUI(ctrl)
  }

  println("Running.")
}