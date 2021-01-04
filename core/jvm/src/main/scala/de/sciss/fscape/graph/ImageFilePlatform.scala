/*
 *  ImageFilePlatform.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.graph

import java.io.{FileNotFoundException, IOException}
import java.net.URI

import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile.{SampleFormat, Spec, Type}
import javax.imageio.ImageIO

trait ImageFilePlatform {
  def readSpec(path: String): Spec = readSpec(new File(path).toURI)

  /** Determines the spec of an image file.
    * A bit of guess work is involved (not tested for float format).
    * JPEG quality is currently _not_ determined.
    */
  def readSpec(uri: URI): Spec = {
    val f       = new File(uri)
    val in      = ImageIO.createImageInputStream(f)
    if (in == null) throw new FileNotFoundException(f.getPath)
    val it      = ImageIO.getImageReaders(in)
    val reader  = if (it.hasNext) it.next() else throw new IOException("Unrecognized image file format")
    try {
      reader.setInput(in)
      val fmt = reader.getFormatName
      val w   = reader.getWidth (0)
      val h   = reader.getHeight(0)
      val s   = reader.getImageTypes(0).next()
      val nc  = s.getNumComponents
      val nb  = s.getColorModel.getPixelSize / nc
      // Ok, that's a guess, LOL
      val st  = if (nb == 8) SampleFormat.Int8 else if (nb == 16) SampleFormat.Int16 else SampleFormat.Float
      val tpe = if (fmt.toLowerCase == "png") Type.PNG else Type.JPG
      Spec(fileType = tpe, sampleFormat = st, width = w, height = h, numChannels = nc)

    } finally {
      reader.dispose()    // XXX TODO --- do we also need to call `in.close()` ?
    }
  }
}
