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
import de.sciss.lucre.{IExpr, Txn}
import de.sciss.lucre.expr.graph.{Const, Ex}
import de.sciss.lucre.expr.{Context, IControl, Model}
import de.sciss.lucre.swing.graph.impl.{ComboBoxValueExpandedImpl, ComponentImpl, ImageFileOutExpandedImpl, PathFieldValueExpandedImpl, SpinnerValueExpandedImpl, Tup2_1Expanded}
import de.sciss.lucre.swing.{Graph, PanelWithPathField, View}
import de.sciss.swingplus.{Spinner, ComboBox => _ComboBox}

object ImageFileOut {
  def apply(): ImageFileOut = Impl()

  final case class Value(w: ImageFileOut) extends Ex[File] {
    type Repr[T <: Txn[T]] = IExpr[T, File]

    override def productPrefix: String = s"ImageFileOut$$Value" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[T].value)
      new PathFieldValueExpandedImpl[T](ws.component.pathField, value0).init()
    }
  }

  final case class FileType(w: ImageFileOut) extends Ex[Int] {
    type Repr[T <: Txn[T]] = IExpr[T, Int]

    override def productPrefix: String = s"ImageFileOut$$FileType" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyFileType)
      val value0    = valueOpt.fold[Int](defaultFileType)(_.expand[T].value)
      val tupVal0   = (value0, None)
      val tup       = new ComboBoxValueExpandedImpl[T, ImageFile.Type](ws.component.fileTypeComboBox, tupVal0).init()
      new Tup2_1Expanded(tup, tx)
    }
  }

  final case class SampleFormat(w: ImageFileOut) extends Ex[Int] {
    type Repr[T <: Txn[T]] = IExpr[T, Int]

    override def productPrefix: String = s"ImageFileOut$$SampleFormat" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keySampleFormat)
      val value0    = valueOpt.fold[Int](defaultSampleFormat)(_.expand[T].value)
      val tupVal0   = (value0, None)
      val tup       = new ComboBoxValueExpandedImpl[T, ImageFile.SampleFormat](ws.component.sampleFormatComboBox, tupVal0).init()
      new Tup2_1Expanded(tup, tx)
    }
  }

  final case class Quality(w: ImageFileOut) extends Ex[Int] {
    type Repr[T <: Txn[T]] = IExpr[T, Int]

    override def productPrefix: String = s"ImageFileOut$$Quality" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyQuality)
      val value0    = valueOpt.fold[Int](defaultQuality)(_.expand[T].value)
      new SpinnerValueExpandedImpl[T, Int](ws.component.qualityField, value0).init()
    }
  }

  final case class Title(w: ImageFileOut) extends Ex[String] {
    type Repr[T <: Txn[T]] = IExpr[T, String]

    override def productPrefix: String = s"ImageFileOut$$Title" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Const(defaultTitle)).expand[T]
    }
  }

  final case class PathFieldVisible(w: ImageFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"ImageFileOut$$PathFieldVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Const(defaultPathFieldVisible)).expand[T]
    }
  }

  final case class FileTypeVisible(w: ImageFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"ImageFileOut$$FileTypeVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFileTypeVisible)
      valueOpt.getOrElse(Const(defaultFileTypeVisible)).expand[T]
    }
  }

  final case class SampleFormatVisible(w: ImageFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"ImageFileOut$$SampleFormatVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keySampleFormatVisible)
      valueOpt.getOrElse(Const(defaultSampleFormatVisible)).expand[T]
    }
  }

  final case class QualityVisible(w: ImageFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"ImageFileOut$$QualityVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyQualityVisible)
      valueOpt.getOrElse(Const(defaultQualityVisible)).expand[T]
    }
  }

  private final case class Impl()
    extends ImageFileOut with ComponentImpl { w =>

    override def productPrefix: String = "ImageFileOut" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] =
      new ImageFileOutExpandedImpl[T](this).initComponent()

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

    def pathFieldVisible: Ex[Boolean] = PathFieldVisible(this)

    def pathFieldVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keyPathFieldVisible, value)
    }

    def fileTypeVisible: Ex[Boolean] = FileTypeVisible(this)

    def fileTypeVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keyFileTypeVisible, value)
    }

    def sampleFormatVisible: Ex[Boolean] = SampleFormatVisible(this)

    def sampleFormatVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keySampleFormatVisible, value)
    }

    def qualityVisible: Ex[Boolean] = QualityVisible(this)

    def qualityVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keyQualityVisible, value)
    }
  }

  private[graph] final val keyFileType            = "fileType"
  private[graph] final val keySampleFormat        = "sampleFormat"
  private[graph] final val keyQuality             = "quality"

  private[graph] final val keyPathFieldVisible    = "pathFieldVisible"
  private[graph] final val keyFileTypeVisible     = "fileTypeVisible"
  private[graph] final val keySampleFormatVisible = "sampleFormatVisible"
  private[graph] final val keyQualityVisible      = "qualityVisible"

  private[graph] final val defaultFileType        = 0 // PNG
  private[graph] final val defaultSampleFormat    = 0 // int8
  private[graph] final val defaultQuality         = 90
  private[graph] final val defaultTitle           = "Select Image Output File"

  private[graph] final val defaultPathFieldVisible    = true
  private[graph] final val defaultFileTypeVisible     = true
  private[graph] final val defaultSampleFormatVisible = true
  private[graph] final val defaultQualityVisible      = true

  abstract class Peer extends PanelWithPathField {
    def fileTypeComboBox    : _ComboBox[ImageFile.Type]
    def sampleFormatComboBox: _ComboBox[ImageFile.SampleFormat]
    def qualityField        : Spinner
  }
}
trait ImageFileOut extends Component {
  type C = ImageFileOut.Peer

  type Repr[T <: Txn[T]] = View.T[T, C] with IControl[T]

  var title       : Ex[String]
  def value       : Model[File]

  def fileType    : Model[Int]
  def sampleFormat: Model[Int]
  def quality     : Model[Int]

  var pathFieldVisible    : Ex[Boolean]
  var fileTypeVisible     : Ex[Boolean]
  var sampleFormatVisible : Ex[Boolean]
  var qualityVisible      : Ex[Boolean]
}
