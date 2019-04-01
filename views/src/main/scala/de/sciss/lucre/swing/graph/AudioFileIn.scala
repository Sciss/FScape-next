/*
 *  AudioFileIn.scala
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

import de.sciss.audiowidgets.AxisFormat
import de.sciss.desktop
import de.sciss.desktop.TextFieldWithPaint
import de.sciss.file.File
import de.sciss.lucre.expr.graph.Constant
import de.sciss.lucre.expr.{Ex, IExpr, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{ComponentExpandedImpl, ComponentImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{Graph, View, deferTx}
import de.sciss.synth.io.{AudioFile, AudioFileSpec, SampleFormat}

import scala.swing.event.ValueChanged
import scala.swing.{Orientation, Swing}
import scala.util.control.NonFatal

object AudioFileIn {
  def apply(): AudioFileIn = Impl()

  private lazy val timeFmt = AxisFormat.Time(hours = false, millis = true)

  def formatSpec(spec: AudioFileSpec): String = {
    val smp     = spec.sampleFormat
    val isFloat = smp match {
      case SampleFormat.Float | SampleFormat.Double => "float"
      case _ => "int"
    }
    val channels = spec.numChannels match {
      case 1 => "mono"
      case 2 => "stereo"
      case n => s"$n-chan."
    }
    val sr  = f"${spec.sampleRate/1000}%1.1f"
    val dur = timeFmt.format(spec.numFrames.toDouble / spec.sampleRate)
    val txt = s"${spec.fileType.name}, $channels ${smp.bitsPerSample}-$isFloat $sr kHz, $dur"
    txt
  }

  private final class Expanded[S <: Sys[S]](protected val w: AudioFileIn) extends View[S]
    with ComponentHolder[Peer] with ComponentExpandedImpl[S] {

    type C = Peer

    override def init()(implicit tx: S#Tx, ctx: Ex.Context[S]): this.type = {
      val valueOpt  = ctx.getProperty[Ex[File   ]](w, PathField.keyValue).map(_.expand[S].value)
      val titleOpt  = ctx.getProperty[Ex[String ]](w, PathField.keyTitle).map(_.expand[S].value)

      deferTx {
        val c: Peer = new scala.swing.BoxPanel(Orientation.Vertical) with Peer {
          private[this] val fmc = {
            val res = new TextFieldWithPaint(27)
            res.editable  = false
            res.focusable = false
            res
          }

          def updateFormat(): Unit =
            pathField.valueOption match {
              case Some(f) =>
                try {
                  val spec  = AudioFile.readSpec(f)
                  fmc.text  = formatSpec(spec)
                  fmc.paint = None
                } catch {
                  case NonFatal(ex) =>
                    fmc.text  = ex.toString
                    fmc.paint = Some(TextFieldWithPaint.RedOverlay)
                }
              case None =>
                fmc.text  = ""
                fmc.paint = None
            }

          val pathField: desktop.PathField = {
            val res = new desktop.PathField
            valueOpt.foreach(res.value = _)
            titleOpt.foreach(res.title = _)
            res.reactions += {
              case ValueChanged(_) => updateFormat()
            }
            res
          }

          updateFormat()

          private[this] val fb = {
            val res = new scala.swing.BoxPanel(Orientation.Horizontal)
            res.contents += fmc
            res.contents += Swing.HStrut(pathField.button.preferredSize.width)
            res
          }

          override lazy val peer: javax.swing.JPanel = {
            val p = new javax.swing.JPanel with SuperMixin {
              override def getBaseline(width: Int, height: Int): Int = {
                val pfj = pathField.peer
                val d   = pfj.getPreferredSize
                val res = pfj.getBaseline(d.width, d.height)
                res + pfj.getY
              }
            }
            val l = new javax.swing.BoxLayout(p, Orientation.Vertical.id)
            p.setLayout(l)
            p
          }

          contents += pathField
          contents += fb
        }

        component = c
      }
      super.init()
    }
  }

  final case class Value(w: AudioFileIn) extends Ex[File] {
    override def productPrefix: String = s"AudioFileIn$$Value" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, File] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[S].value)
      new PathFieldValueExpandedImpl[S](ws.component.pathField, value0).init()
    }
  }

  final case class Title(w: AudioFileIn) extends Ex[String] {
    override def productPrefix: String = s"AudioFileIn$$Title" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, String] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Constant("Select Audio Input File")).expand[S]
    }
  }

  private final case class Impl() extends AudioFileIn with ComponentImpl { w =>
    override def productPrefix: String = "AudioFileIn" // serialization

    protected def mkControl[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): Repr[S] =
      new Expanded[S](this).init()

    object value extends Model[File] {
      def apply(): Ex[File] = Value(w)

      def update(value: Ex[File]): Unit = {
        val b = Graph.builder
        b.putProperty(w, PathField.keyValue, value)
      }
    }

    def title: Ex[String] = Title(this)

    def title_=(value: Ex[String]): Unit = {
      val b = Graph.builder
      b.putProperty(this, PathField.keyTitle, value)
    }
  }

  trait Peer extends scala.swing.Panel {
    def pathField: desktop.PathField
  }
}
trait AudioFileIn extends Component {
  type C = AudioFileIn.Peer

  var title : Ex[String]
  def value : Model[File]
}
