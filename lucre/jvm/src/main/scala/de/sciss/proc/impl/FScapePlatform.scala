package de.sciss.proc.impl

import de.sciss.fscape.Graph

trait FScapePlatform {
  private lazy val _init: Unit = {
    Graph.addProductReaderSq({
      import de.sciss.fscape.graph._
      import de.sciss.fscape.lucre.{graph => l}
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
        // lucre
        l.ImageFileIn, l.ImageFileIn.Width, l.ImageFileIn.Height,
        l.ImageFileOut, l.ImageFileOut.WithFile,
        l.ImageFileSeqIn, l.ImageFileSeqIn.Width, l.ImageFileSeqIn.Height,
        l.ImageFileSeqOut, l.ImageFileSeqOut.WithFile,
        l.MkAudioCue,
      )
    })
  }

  protected def initPlatform(): Unit = _init
}
