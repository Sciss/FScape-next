package de.sciss.fscape
package tests

import de.sciss.file._
import de.sciss.fscape.Ops._
import de.sciss.tsp.{LinKernighan, Point => Pt}

object LinKernighanTest extends App {
  val pts = Vector(
    Pt(20833.3333, 17100.0000),
    Pt(20900.0000, 17066.6667),
    Pt(21300.0000, 13016.6667),
    Pt(21600.0000, 14150.0000),
    Pt(21600.0000, 14966.6667),
    Pt(21600.0000, 16500.0000),
    Pt(22183.3333, 13133.3333),
    Pt(22583.3333, 14300.0000),
    Pt(22683.3333, 12716.6667),
    Pt(23616.6667, 15866.6667),
    Pt(23700.0000, 15933.3333),
    Pt(23883.3333, 14533.3333),
    Pt(24166.6667, 13250.0000),
    Pt(25149.1667, 12365.8333),
    Pt(26133.3333, 14500.0000),
    Pt(26150.0000, 10550.0000),
    Pt(26283.3333, 12766.6667),
    Pt(26433.3333, 13433.3333),
    Pt(26550.0000, 13850.0000),
    Pt(26733.3333, 11683.3333),
    Pt(27026.1111, 13051.9444),
    Pt(27096.1111, 13415.8333),
    Pt(27153.6111, 13203.3333),
    Pt(27166.6667,  9833.3333),
    Pt(27233.3333, 10450.0000),
    Pt(27233.3333, 11783.3333),
    Pt(27266.6667, 10383.3333),
    Pt(27433.3333, 12400.0000),
    Pt(27462.5000, 12992.2222),
  )
  
  val size    = pts.size
  val tour0   = LinKernighan.createRandomTour(size, seed = 0L)
  val weights0 = for { i <- 0 until size; j <- i + 1 until size} yield {
    val a = pts(i)
    val b = pts(j)
    a distance b
  }
  
/*

expected output:

cost: 28019.89775833068
out: 1
out: 0
out: 4
out: 3
out: 7
out: 2
out: 6
out: 8
out: 12
out: 13
out: 16
out: 19
out: 15
out: 23
out: 26
out: 24
out: 25
out: 27
out: 28
out: 20
out: 22
out: 21
out: 17
out: 18
out: 14
out: 11
out: 10
out: 9
out: 5

 */

  def any2stringadd: Any = ()

  val g = Graph {
    import graph._
    val init    : GE = tour0    .map(ConstantI(_): GE).reduce(_ ++ _)
    val weights : GE = weights0 .map(ConstantD(_): GE).reduce(_ ++ _)
    val lk = LinKernighanTSP(init = init, weights = weights, size = size)
    lk.cost.poll("cost")
    val sliceIndices = lk.tour
    sliceIndices.poll(DC(1), "out")

//    val chunkSize = 65536
//    val spanStart = sliceIndices * chunkSize
//    val spanStop  = spanStart + chunkSize
//    val spans     = spanStart zip spanStop
//    Length(spans).poll("spans.length")
  }

  val g2 = Graph {
    import graph._
    // bug -- stops at `lk runs: 6144` without flushing
    // numEdges: 528
    // numChunks: 8038.0

    def mkIn() = AudioFileIn(file("/data/audio_work/beyond-s1-living-room.aif").toURI, numChannels = 1)
    val in        = mkIn()
    val fftSize   = 64
    val numMag    = fftSize/2 + 1
    val numEdges  = numMag * (numMag - 1) / 2
    println(s"numMag = $numMag")      // numMag.poll("numMag")
    println(s"numEdges = $numEdges")  // numEdges.poll("numEdges")

    val numFramesSl = 4000 // in.numFrames
    val numChunks = ((numFramesSl + (fftSize - 1)) / fftSize).floor
    println(s"numChunks = $numChunks")  // numChunks.poll("numChunks")

    val inW = in.take(numFramesSl)
    val inF       = Real1FFT(inW, fftSize, mode = 1)
    val mag       = inF.complex.mag
    Length(mag).poll("mag.length")

    def chunkSeq  = ArithmSeq(length = numMag)
    val chunkA    = RepeatWindow(chunkSeq, 1     , numMag)
    val chunkB    = RepeatWindow(chunkSeq, numMag, numMag)
    val guard     = chunkA < chunkB
    val startA0   = FilterSeq(chunkA, guard)
    val startB0   = FilterSeq(chunkB, guard)

    val startA  = RepeatWindow(startA0, numEdges, numChunks)
    val startB  = RepeatWindow(startB0, numEdges, numChunks)

    //Length(startA).poll("startA.length")
    //Length(startB).poll("startB.length")

    val aBins = WindowApply(RepeatWindow(mag, numMag, numEdges),
      size = numMag, index = startA)
    val bBins = WindowApply(RepeatWindow(mag, numMag, numEdges),
      size = numMag, index = startB)

    //Length(aBins).poll("aBins.length")
    //Length(bBins).poll("bBins.length")

    val aW    = aBins * fftSize // * awt
    val bW    = bBins * fftSize // * awt

    val corr  = aW absDif bW // Pearson(aW, bW, size = 1)
    Length(corr).poll("corr.length")

    //33*63
    //528*63

    val sliceIndices: GE = {
      val init0   = ArithmSeq(length = numMag)
      val init = RepeatWindow(init0, numMag, numChunks)
      Length(init).poll("init.length")
      val weights = corr // corr.max(-320.ampDb).reciprocal // 1.0 - corr // high correlation = low cost
      Length(weights).poll("weights.length")
      val lk      = LinKernighanTSP(init = init, weights = weights, size = numMag)
      Frames(lk.cost).poll(1, "lk runs")
      val t       = lk.tour
      t
    }

    Length(sliceIndices).poll("result")

  }

  stream.Control().run(g2)
}
