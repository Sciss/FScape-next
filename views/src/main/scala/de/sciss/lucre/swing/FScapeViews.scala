package de.sciss.lucre.swing

import de.sciss.lucre.expr.ExElem

object FScapeViews {
  private lazy val _init: Unit = {
    ExElem.addProductReaderSq({
      import graph._
      Seq(
        AudioFileIn, AudioFileIn.Value, AudioFileIn.Title, AudioFileIn.PathFieldVisible, AudioFileIn.FormatVisible,
        AudioFileOut, AudioFileOut.Value, AudioFileOut.FileType, AudioFileOut.SampleFormat, AudioFileOut.SampleRate,
        AudioFileOut.Title, AudioFileOut.PathFieldVisible, AudioFileOut.FileTypeVisible,
        AudioFileOut.SampleFormatVisible, AudioFileOut.SampleRateVisible,
        ImageFileIn, ImageFileIn.Value, ImageFileIn.Title, ImageFileIn.PathFieldVisible, ImageFileIn.FormatVisible,
        ImageFileOut, ImageFileOut.Value, ImageFileOut.FileType, ImageFileOut.SampleFormat, ImageFileOut.Quality,
        ImageFileOut.Title, ImageFileOut.PathFieldVisible, ImageFileOut.FileTypeVisible,
        ImageFileOut.SampleFormatVisible, ImageFileOut.QualityVisible,
      )
    })
  }

  def init(): Unit = _init
}
