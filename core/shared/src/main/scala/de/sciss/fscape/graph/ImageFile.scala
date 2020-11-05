/*
 *  ImageFile.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.serial.{ConstFormat, DataInput, DataOutput}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFile extends ImageFilePlatform {
  object Type {
    case object PNG extends Type {
      override def productPrefix = s"ImageFile$$Type$$PNG$$"    // serialization

      final val id = 0

      val name = "PNG"

      val extension = "png"

      val extensions: Vec[String] = Vector("png")

      val isLossy = false
    }
    case object JPG extends Type {
      override def productPrefix = s"ImageFile$$Type$$JPG$$"    // serialization

      final val id = 1

      val name = "JPEG"

      val extension = "jpg"

      val extensions: Vec[String] = Vector("jpg", "jpeg")

      val isLossy = true
    }

    def apply(id: Int): Type = id match {
      case PNG.id => PNG
      case JPG.id => JPG
    }

    val writable: Vec[Type] = Vector(PNG, JPG)
    def readable: Vec[Type] = writable
  }
  sealed trait Type {
    def id: Int

    def name: String

    /** @return  the default extension (period not included) */
    def extension: String

    /** @return  a list of alternative extensions, including the default `extension` */
    def extensions: Vec[String]

    def isLossy: Boolean
  }

  object SampleFormat {
    case object Int8 extends SampleFormat {
      override def productPrefix = s"ImageFile$$SampleFormat$$Int8$$"   // serialization

      final val id = 0

      val bitsPerSample = 8
    }
    case object Int16 extends SampleFormat {
      override def productPrefix = s"ImageFile$$SampleFormat$$Int16$$"  // serialization

      final val id = 1

      val bitsPerSample = 16
    }

    /** Currently not supported (ImageIO) */
    case object Float extends SampleFormat {
      override def productPrefix = s"ImageFile$$SampleFormat$$Float$$"  // serialization

      final val id = 2

      val bitsPerSample = 32
    }

    def apply(id: Int): SampleFormat = (id: @switch) match {
      case Int8  .id => Int8
      case Int16 .id => Int16
      case Float .id => Float
    }
  }
  sealed trait SampleFormat {
    def id: Int

    def bitsPerSample: Int
  }

  object Spec {
    implicit object format extends ConstFormat[Spec] {
      def write(v: Spec, out: DataOutput): Unit = {
        import v._
        out.writeInt(fileType    .id)
        out.writeInt(sampleFormat.id)
        out.writeInt(width          )
        out.writeInt(height         )
        out.writeInt(numChannels    )
        out.writeInt(quality        )
      }

      def read(in: DataInput): Spec = {
        val fileType      = Type        (in.readInt())
        val sampleFormat  = SampleFormat(in.readInt())
        val width         = in.readInt()
        val height        = in.readInt()
        val numChannels   = in.readInt()
        val quality       = in.readInt()
        Spec(fileType, sampleFormat, width = width, height = height, numChannels = numChannels, quality = quality)
      }
    }
  }
  /** @param quality  only used for JPEG
    */
  final case class Spec(fileType     : Type         = Type.PNG,
                        sampleFormat : SampleFormat = SampleFormat.Int8,
                        width        : Int,
                        height       : Int,
                        numChannels  : Int,
                        quality      : Int = 80) {

    override def productPrefix = s"ImageFile$$Spec"  // serialization
  }
}