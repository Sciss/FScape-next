/*
 *  AudioFileOut.scala
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
import de.sciss.lucre.expr.graph.{Const, Ex}
import de.sciss.lucre.expr.{Context, IControl, IExpr, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{AudioFileOutExpandedImpl, ComboBoxIndexExpandedImpl, ComboBoxValueExpandedImpl, ComponentImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField, View}
import de.sciss.swingplus.{ComboBox => _ComboBox}
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileType

object AudioFileOut {
  def apply(): AudioFileOut = Impl()

  final case class Value(w: AudioFileOut) extends Ex[File] {
    type Repr[S <: Sys[S]] = IExpr[S, File]

    override def productPrefix: String = s"AudioFileOut$$Value" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[S].value)
      new PathFieldValueExpandedImpl[S](ws.component.pathField, value0).init()
    }
  }

  final case class FileType(w: AudioFileOut) extends Ex[Int] {
    type Repr[S <: Sys[S]] = IExpr[S, Int]

    override def productPrefix: String = s"AudioFileOut$$FileType" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyFileType)
      val value0    = valueOpt.fold[Int](defaultFileType)(_.expand[S].value)
      new ComboBoxIndexExpandedImpl[S, AudioFileType](ws.component.fileTypeComboBox, value0).init()
    }
  }

  final case class SampleFormat(w: AudioFileOut) extends Ex[Int] {
    type Repr[S <: Sys[S]] = IExpr[S, Int]

    override def productPrefix: String = s"AudioFileOut$$SampleFormat" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keySampleFormat)
      val value0    = valueOpt.fold[Int](defaultSampleFormat)(_.expand[S].value)
      new ComboBoxIndexExpandedImpl[S, io.SampleFormat](ws.component.sampleFormatComboBox, value0).init()
    }
  }

  final case class SampleRate(w: AudioFileOut) extends Ex[Double] {
    type Repr[S <: Sys[S]] = IExpr[S, Double]

    override def productPrefix: String = s"AudioFileOut$$SampleRate" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Double]](w, keySampleRate)
      val value0    = valueOpt.fold[Double](defaultSampleRate)(_.expand[S].value)
      new ComboBoxValueExpandedImpl[S, Double](ws.component.sampleRateComboBox, value0).init()
    }
  }

  final case class Title(w: AudioFileOut) extends Ex[String] {
    type Repr[S <: Sys[S]] = IExpr[S, String]

    override def productPrefix: String = s"AudioFileOut$$Title" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Const(defaultTitle)).expand[S]
    }
  }

  final case class PathFieldVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[S <: Sys[S]] = IExpr[S, Boolean]

    override def productPrefix: String = s"AudioFileOut$$PathFieldVisible" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Const(defaultPathFieldVisible)).expand[S]
    }
  }

  final case class FileTypeVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[S <: Sys[S]] = IExpr[S, Boolean]

    override def productPrefix: String = s"AudioFileOut$$FileTypeVisible" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFileTypeVisible)
      valueOpt.getOrElse(Const(defaultFileTypeVisible)).expand[S]
    }
  }

  final case class SampleFormatVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[S <: Sys[S]] = IExpr[S, Boolean]

    override def productPrefix: String = s"AudioFileOut$$SampleFormatVisible" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keySampleFormatVisible)
      valueOpt.getOrElse(Const(defaultSampleFormatVisible)).expand[S]
    }
  }

  final case class SampleRateVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[S <: Sys[S]] = IExpr[S, Boolean]

    override def productPrefix: String = s"AudioFileOut$$SampleRateVisible" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keySampleRateVisible)
      valueOpt.getOrElse(Const(defaultSampleRateVisible)).expand[S]
    }
  }

  private final case class Impl()
    extends AudioFileOut with ComponentImpl { w =>

    override def productPrefix: String = "AudioFileOut" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] =
      new AudioFileOutExpandedImpl[S](this).initComponent()

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

    object sampleRate extends Model[Double] {
      def apply(): Ex[Double] = SampleRate(w)

      def update(value: Ex[Double]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keySampleRate, value)
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

    def sampleRateVisible: Ex[Boolean] = SampleRateVisible(this)

    def sampleRateVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keySampleRateVisible, value)
    }
  }

  private[graph] final val keyFileType                = "fileType"
  private[graph] final val keySampleFormat            = "sampleFormat"
  private[graph] final val keySampleRate              = "sampleRate"

  private[graph] final val keyPathFieldVisible        = "pathFieldVisible"
  private[graph] final val keyFileTypeVisible         = "fileTypeVisible"
  private[graph] final val keySampleFormatVisible     = "sampleFormatVisible"
  private[graph] final val keySampleRateVisible       = "sampleRateVisible"

  private[graph] final val defaultFileType            = 0 // AIFF
  private[graph] final val defaultSampleFormat        = 1 // int24
  private[graph] final val defaultSampleRate          = 44100.0
  private[graph] final val defaultTitle               = "Select Audio Output File"

  private[graph] final val defaultPathFieldVisible    = true
  private[graph] final val defaultFileTypeVisible     = true
  private[graph] final val defaultSampleFormatVisible = true
  private[graph] final val defaultSampleRateVisible   = false

  abstract class Peer extends PanelWithPathField {
    def fileTypeComboBox    : _ComboBox[AudioFileType]
    def sampleFormatComboBox: _ComboBox[io.SampleFormat]
    def sampleRateComboBox  : _ComboBox[Double]
  }
}
trait AudioFileOut extends Component {
  type C = AudioFileOut.Peer

  type Repr[S <: Sys[S]] = View.T[S, C] with IControl[S]

  var title       : Ex[String]
  def value       : Model[File]

  def fileType    : Model[Int]
  def sampleFormat: Model[Int]
  def sampleRate  : Model[Double]

  var pathFieldVisible   : Ex[Boolean]
  var fileTypeVisible    : Ex[Boolean]
  var sampleFormatVisible: Ex[Boolean]
  var sampleRateVisible  : Ex[Boolean]
}
