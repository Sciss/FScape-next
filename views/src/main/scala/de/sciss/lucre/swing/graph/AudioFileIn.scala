/*
 *  AudioFileIn.scala
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

import java.io.FileNotFoundException
import java.net.URI
import de.sciss.audiowidgets.AxisFormat
import de.sciss.file.File
import de.sciss.lucre.expr.graph.{Const, Ex}
import de.sciss.lucre.expr.{Context, IControl, Model}
import de.sciss.lucre.swing.graph.impl.{ComponentImpl, FileInExpandedImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField, View}
import de.sciss.lucre.{IExpr, Txn}
import de.sciss.audiofile.{AudioFile, AudioFileSpec, SampleFormat}
import de.sciss.lucre.expr.ExElem.{ProductReader, RefMapIn}

object AudioFileIn extends ProductReader[AudioFileIn] {
  def apply(): AudioFileIn = Impl()

  override def read(in: RefMapIn, key: String, arity: Int, adj: Int): AudioFileIn = {
    require (arity == 0 && adj == 0)
    AudioFileIn()
  }

  private lazy val timeFmt = AxisFormat.Time(hours = false, millis = true)

  def specToString(spec: AudioFileSpec): String = {
    import spec._
    val smpFmt  = formatToString(sampleFormat)
    val channels = spec.numChannels match {
      case 1 => "mono"
      case 2 => "stereo"
      case n => s"$n-chan."
    }
    val sr  = f"${sampleRate/1000}%1.1f"
    val dur = timeFmt.format(numFrames.toDouble / sampleRate)
    val txt = s"${fileType.name}, $channels $smpFmt $sr kHz, $dur"
    txt
  }

  def formatToString(smp: SampleFormat): String = {
    val smpTpe = smp match {
      case SampleFormat.Float | SampleFormat.Double => "float"
      case SampleFormat.UInt8                       => "uint"
      case _                                        => "int"
    }
    val txt = s"${smp.bitsPerSample}-bit $smpTpe"
    txt
  }

  private final class Expanded[T <: Txn[T]](protected val peer: AudioFileIn) extends FileInExpandedImpl[T] {
    protected def mkFormat(uri: URI): String = {
      if (!uri.isAbsolute) throw new FileNotFoundException(uri.toString)
      val f     = new File(uri)   // XXX TODO
      val spec  = AudioFile.readSpec(f)
      specToString(spec)
    }
  }

  object Value extends ProductReader[Value] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Value = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileIn]()
      new Value(_w)
    }
  }
  final case class Value(w: AudioFileIn) extends Ex[URI] {
    type Repr[T <: Txn[T]] = IExpr[T, URI]

    override def productPrefix: String = s"AudioFileIn$$Value" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[URI]](w, PathField.keyValue)
      val value0    = valueOpt.fold[URI](PathField.defaultValue)(_.expand[T].value)
      new PathFieldValueExpandedImpl[T](ws.component.pathField, value0).init()
    }
  }

  object Title extends ProductReader[Title] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Title = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileIn]()
      new Title(_w)
    }
  }
  final case class Title(w: AudioFileIn) extends Ex[String] {
    type Repr[T <: Txn[T]] = IExpr[T, String]

    override def productPrefix: String = s"AudioFileIn$$Title" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Const(defaultTitle)).expand[T]
    }
  }

  object PathFieldVisible extends ProductReader[PathFieldVisible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): PathFieldVisible = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileIn]()
      new PathFieldVisible(_w)
    }
  }
  final case class PathFieldVisible(w: AudioFileIn) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileIn$$PathFieldVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Const(defaultPathFieldVisible)).expand[T]
    }
  }

  object FormatVisible extends ProductReader[FormatVisible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): FormatVisible = {
      require (arity == 1 && adj == 0)
      val _w = in.readProductT[AudioFileIn]()
      new FormatVisible(_w)
    }
  }
  final case class FormatVisible(w: AudioFileIn) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileIn$$FormatVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFormatVisible)
      valueOpt.getOrElse(Const(defaultFormatVisible)).expand[T]
    }
  }

  private final case class Impl() extends AudioFileIn with ComponentImpl { w =>
    override def productPrefix: String = "AudioFileIn" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] =
      new Expanded[T](this).initComponent()

    object value extends Model[URI] {
      def apply(): Ex[URI] = Value(w)

      def update(value: Ex[URI]): Unit = {
        val b = Graph.builder
        b.putProperty(w, PathField.keyValue, value)
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

    def formatVisible: Ex[Boolean] = FormatVisible(this)

    def formatVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keyFormatVisible, value)
    }
  }

  private[graph] val keyPathFieldVisible  = "pathFieldVisible"
  private[graph] val keyFormatVisible     = "formatVisible"
  private[graph] val defaultTitle         = "Select Audio Input File"
  
  private[graph] val defaultPathFieldVisible  = true
  private[graph] val defaultFormatVisible     = true

  type Peer = PanelWithPathField
}
trait AudioFileIn extends Component {
  type C = AudioFileIn.Peer

  type Repr[T <: Txn[T]] = View.T[T, C] with IControl[T]

  var title: Ex[String]

  def value: Model[URI]
  
  var pathFieldVisible: Ex[Boolean]
  var formatVisible   : Ex[Boolean]
}
