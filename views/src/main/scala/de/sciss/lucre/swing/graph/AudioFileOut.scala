/*
 *  AudioFileOut.scala
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

package de.sciss.lucre.swing.graph

import java.net.URI
import de.sciss.lucre.expr.graph.{Const, Ex}
import de.sciss.lucre.expr.{Context, IControl, Model}
import de.sciss.lucre.swing.graph.impl.{AudioFileOutExpandedImpl, ComboBoxValueExpandedImpl, ComponentImpl, PathFieldValueExpandedImpl, Tup2_1Expanded, Tup2_2OptExpanded}
import de.sciss.lucre.swing.{Graph, PanelWithPathField, View}
import de.sciss.lucre.{IExpr, Txn}
import de.sciss.swingplus.{ComboBox => _ComboBox}
import de.sciss.audiofile.{AudioFileType, SampleFormat => _SampleFormat}
import de.sciss.lucre.expr.ExElem.{ProductReader, RefMapIn}

object AudioFileOut extends ProductReader[AudioFileOut] {
  def apply(): AudioFileOut = Impl()

  override def read(in: RefMapIn, key: String, arity: Int, adj: Int): AudioFileOut = {
    require (arity == 0 && adj == 0)
    AudioFileOut()
  }

  object Value extends ProductReader[Value] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Value = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new Value(_w)
    }
  }
  final case class Value(w: AudioFileOut) extends Ex[URI] {
    type Repr[T <: Txn[T]] = IExpr[T, URI]

    override def productPrefix: String = s"AudioFileOut$$Value" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[URI]](w, PathField.keyValue)
      val value0    = valueOpt.fold[URI](PathField.defaultValue)(_.expand[T].value)
      new PathFieldValueExpandedImpl[T](ws.component.pathField, value0).init()
    }
  }

  object FileType extends ProductReader[FileType] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): FileType = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new FileType(_w)
    }
  }
  final case class FileType(w: AudioFileOut) extends Ex[Int] {
    type Repr[T <: Txn[T]] = IExpr[T, Int]

    override def productPrefix: String = s"AudioFileOut$$FileType" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyFileType)
      val value0    = valueOpt.fold[Int](defaultFileType)(_.expand[T].value)
      val tupVal0   = (value0, None) // AudioFileType
      val tup       = new ComboBoxValueExpandedImpl[T, AudioFileType](ws.component.fileTypeComboBox, tupVal0).init()
      new Tup2_1Expanded(tup, tx)
    }
  }

  object SampleFormat extends ProductReader[SampleFormat] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): SampleFormat = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new SampleFormat(_w)
    }
  }
  final case class SampleFormat(w: AudioFileOut) extends Ex[Int] {
    type Repr[T <: Txn[T]] = IExpr[T, Int]

    override def productPrefix: String = s"AudioFileOut$$SampleFormat" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keySampleFormat)
      val value0    = valueOpt.fold[Int](defaultSampleFormat)(_.expand[T].value)
      val tupVal0   = (value0, None)
      val tup       = new ComboBoxValueExpandedImpl[T, _SampleFormat](ws.component.sampleFormatComboBox, tupVal0).init()
      new Tup2_1Expanded(tup, tx)
    }
  }

  object SampleRate extends ProductReader[SampleRate] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): SampleRate = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new SampleRate(_w)
    }
  }
  final case class SampleRate(w: AudioFileOut) extends Ex[Double] {
    type Repr[T <: Txn[T]] = IExpr[T, Double]

    override def productPrefix: String = s"AudioFileOut$$SampleRate" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[Double]](w, keySampleRate)
      val value0    = valueOpt.fold[Double](defaultSampleRate)(_.expand[T].value)
      val tupVal0   = (-1, Some(value0))
      val tup       = new ComboBoxValueExpandedImpl[T, Double](ws.component.sampleRateComboBox, tupVal0).init()
      new Tup2_2OptExpanded(tup, defaultSampleRate, tx)
    }
  }

  object Title extends ProductReader[Title] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Title = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new Title(_w)
    }
  }
  final case class Title(w: AudioFileOut) extends Ex[String] {
    type Repr[T <: Txn[T]] = IExpr[T, String]

    override def productPrefix: String = s"AudioFileOut$$Title" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Const(defaultTitle)).expand[T]
    }
  }

  object PathFieldVisible extends ProductReader[PathFieldVisible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): PathFieldVisible = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new PathFieldVisible(_w)
    }
  }
  final case class PathFieldVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileOut$$PathFieldVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Const(defaultPathFieldVisible)).expand[T]
    }
  }

  object FileTypeVisible extends ProductReader[FileTypeVisible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): FileTypeVisible = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new FileTypeVisible(_w)
    }
  }
  final case class FileTypeVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileOut$$FileTypeVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFileTypeVisible)
      valueOpt.getOrElse(Const(defaultFileTypeVisible)).expand[T]
    }
  }

  object SampleFormatVisible extends ProductReader[SampleFormatVisible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): SampleFormatVisible = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new SampleFormatVisible(_w)
    }
  }
  final case class SampleFormatVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileOut$$SampleFormatVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keySampleFormatVisible)
      valueOpt.getOrElse(Const(defaultSampleFormatVisible)).expand[T]
    }
  }

  object SampleRateVisible extends ProductReader[SampleRateVisible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): SampleRateVisible = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileOut]()
      new SampleRateVisible(_w)
    }
  }
  final case class SampleRateVisible(w: AudioFileOut) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileOut$$SampleRateVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keySampleRateVisible)
      valueOpt.getOrElse(Const(defaultSampleRateVisible)).expand[T]
    }
  }

  private final case class Impl()
    extends AudioFileOut with ComponentImpl { w =>

    override def productPrefix: String = "AudioFileOut" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] =
      new AudioFileOutExpandedImpl[T](this).initComponent()

    object value extends Model[URI] {
      def apply(): Ex[URI] = Value(w)

      def update(value: Ex[URI]): Unit = {
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
    def sampleFormatComboBox: _ComboBox[_SampleFormat]
    def sampleRateComboBox  : _ComboBox[Double]
  }
}
trait AudioFileOut extends Component {
  type C = AudioFileOut.Peer

  type Repr[T <: Txn[T]] = View.T[T, C] with IControl[T]

  var title       : Ex[String]

  def value       : Model[URI]

  def fileType    : Model[Int]
  def sampleFormat: Model[Int]
  def sampleRate  : Model[Double]

  var pathFieldVisible   : Ex[Boolean]
  var fileTypeVisible    : Ex[Boolean]
  var sampleFormatVisible: Ex[Boolean]
  var sampleRateVisible  : Ex[Boolean]
}
