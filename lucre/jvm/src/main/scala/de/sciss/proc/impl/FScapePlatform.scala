package de.sciss.proc.impl

import de.sciss.fscape.Graph

trait FScapePlatform {
  private lazy val _init: Unit = {
    Graph.addProductReaderSq({
      import de.sciss.fscape.graph._
      Seq(
        AudioFileIn,
        AudioFileOut,
        // XXX TODO: AudioFileSpec
        BufferDisk,
        Fourier,
        ImageFileIn,
        ImageFileOut,
        ImageFileSeqIn,
        ImageFileSeqOut,
        PitchAC,
        Plot1D,
        ResampleWindow,
        Sheet1D,
        Slices,
        WPE_ReverbFrame, WPE_Dereverberate,
      )
    })
  }

  protected def initPlatform(): Unit = _init
}
