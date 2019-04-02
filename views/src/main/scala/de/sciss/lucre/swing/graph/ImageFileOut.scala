/*
 *  ImageFileOut.scala
 *  (LucreSwing)
 *
 *  Copyright (c) 2014-2019 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.lucre.swing.graph

import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile
import de.sciss.lucre.expr.graph.Constant
import de.sciss.lucre.expr.{Ex, IExpr, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{ComboBoxIndexExpandedImpl, ComponentImpl, ImageFileOutExpandedImpl, PathFieldValueExpandedImpl, SpinnerValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField}
import de.sciss.swingplus.{Spinner, ComboBox => _ComboBox}

object ImageFileOut {
  def apply(pathFieldVisible    : Boolean = true,
            fileTypeVisible     : Boolean = true,
            sampleFormatVisible : Boolean = true,
            qualityVisible      : Boolean = true): ImageFileOut =
    Impl(pathFieldVisible = pathFieldVisible, fileTypeVisible = fileTypeVisible,
      sampleFormatVisible = sampleFormatVisible, qualityVisible = qualityVisible)

  final case class Value(w: ImageFileOut) extends Ex[File] {
    override def productPrefix: String = s"ImageFileOut$$Value" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, File] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[S].value)
      new PathFieldValueExpandedImpl[S](ws.component.pathField, value0).init()
    }
  }

  final case class FileType(w: ImageFileOut) extends Ex[Int] {
    override def productPrefix: String = s"ImageFileOut$$FileType" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Int] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyFileType)
      val value0    = valueOpt.fold[Int](defaultFileType)(_.expand[S].value)
      new ComboBoxIndexExpandedImpl[S, ImageFile.Type](ws.component.fileTypeComboBox, value0).init()
    }
  }

  final case class SampleFormat(w: ImageFileOut) extends Ex[Int] {
    override def productPrefix: String = s"ImageFileOut$$SampleFormat" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Int] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keySampleFormat)
      val value0    = valueOpt.fold[Int](defaultSampleFormat)(_.expand[S].value)
      new ComboBoxIndexExpandedImpl[S, ImageFile.SampleFormat](ws.component.sampleFormatComboBox, value0).init()
    }
  }

  final case class Quality(w: ImageFileOut) extends Ex[Int] {
    override def productPrefix: String = s"ImageFileOut$$Quality" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Int] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyQuality)
      val value0    = valueOpt.fold[Int](defaultQuality)(_.expand[S].value)
      new SpinnerValueExpandedImpl[S, Int](ws.component.qualityField, value0).init()
    }
  }

  final case class Title(w: ImageFileOut) extends Ex[String] {
    override def productPrefix: String = s"ImageFileOut$$Title" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, String] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Constant(defaultTitle)).expand[S]
    }
  }

  private final case class Impl(pathFieldVisible    : Boolean,
                                fileTypeVisible     : Boolean,
                                sampleFormatVisible : Boolean,
                                qualityVisible      : Boolean)
    extends ImageFileOut with ComponentImpl { w =>

    override def productPrefix: String = "ImageFileOut" // serialization

    protected def mkControl[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): Repr[S] =
      new ImageFileOutExpandedImpl[S](this,
        pathFieldVisible    = pathFieldVisible,
        fileTypeVisible     = fileTypeVisible,
        sampleFormatVisible = sampleFormatVisible,
        qualityVisible      = qualityVisible
      ).init()

    object value extends Model[File] {
      def apply(): Ex[File] = Value(w)

      def update(value: Ex[File]): Unit = {
        val b = Graph.builder
        b.putProperty(w, PathField.keyValue, value)
      }
    }

    object fileType extends Model[Int] {
      def apply(): Ex[Int] = FileType(w)

      def update(value: Ex[Int]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keyFileType, value)
      }
    }

    object sampleFormat extends Model[Int] {
      def apply(): Ex[Int] = SampleFormat(w)

      def update(value: Ex[Int]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keySampleFormat, value)
      }
    }

    object quality extends Model[Int] {
      def apply(): Ex[Int] = Quality(w)

      def update(value: Ex[Int]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keyQuality, value)
      }
    }

    def title: Ex[String] = Title(this)

    def title_=(value: Ex[String]): Unit = {
      val b = Graph.builder
      b.putProperty(this, PathField.keyTitle, value)
    }
  }

  private[graph] final val keyFileType          = "fileType"
  private[graph] final val keySampleFormat      = "sampleFormat"
  private[graph] final val keyQuality           = "quality"

  private[graph] final val defaultFileType      = 0 // PNG
  private[graph] final val defaultSampleFormat  = 0 // int8
  private[graph] final val defaultQuality       = 90
  private[graph] final val defaultTitle         = "Select Image Output File"

  abstract class Peer extends PanelWithPathField {
    def fileTypeComboBox    : _ComboBox[ImageFile.Type]
    def sampleFormatComboBox: _ComboBox[ImageFile.SampleFormat]
    def qualityField        : Spinner
  }
}
trait ImageFileOut extends Component {
  type C = ImageFileOut.Peer

  var title       : Ex[String]
  def value       : Model[File]

  def fileType    : Model[Int]
  def sampleFormat: Model[Int]
  def quality     : Model[Int]
}
