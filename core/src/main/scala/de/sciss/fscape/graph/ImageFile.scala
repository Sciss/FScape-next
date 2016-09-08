/*
 *  ImageFile.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.graph

object ImageFile {
  object Type {
    case object PNG extends Type
    case object JPG extends Type
  }
  sealed trait Type

  object SampleFormat {
    case object Int8  extends SampleFormat
    case object Int16 extends SampleFormat

    /** Currently not supported (ImageIO) */
    case object Float extends SampleFormat
  }
  sealed trait SampleFormat

  /** @param quality  only used for JPEG
    */
  final case class Spec(fileType     : Type         = Type.PNG,
                        sampleFormat : SampleFormat = SampleFormat.Int8,
                        width        : Int,
                        height       : Int,
                        numChannels  : Int,
                        quality      : Int = 80)
}
