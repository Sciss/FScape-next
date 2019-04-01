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
import de.sciss.lucre.expr.ExOps._
import de.sciss.lucre.expr.{Ex, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{ComponentExpandedImpl, ComponentImpl}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
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

  private final class Expanded[S <: Sys[S]](protected val w: Impl /* AudioFileIn */) extends View[S]
    with ComponentHolder[scala.swing.Panel] with ComponentExpandedImpl[S] {

    type C = scala.swing.Panel

    override def init()(implicit tx: S#Tx, ctx: Ex.Context[S]): this.type = {
      val pfx: View.T[S, desktop.PathField]     = w.pathField   .expand[S]
//      val fmx: View.T[S, scala.swing.TextField] = w.formatField .expand[S]

      deferTx {
        val pfc = pfx.component
//        val fmc = fmx.component
        val fmc = new TextFieldWithPaint(27)
//        val d0  = pfc.textField.preferredSize
//        val d1  = fmc.preferredSize
//        d1.width = d0.width
//        fmc.preferredSize = d1
        fmc.editable  = false
        fmc.focusable = false
        val fb = new scala.swing.BoxPanel(Orientation.Horizontal)
        fb.contents += fmc
        fb.contents += Swing.HStrut(pfc.button.preferredSize.width)
        val c = new scala.swing.BoxPanel(Orientation.Vertical) {
          override lazy val peer: javax.swing.JPanel = {
            val p = new javax.swing.JPanel with SuperMixin {
              override def getBaseline(width: Int, height: Int): Int = {
                val pfj = pfc.peer
                val d   = pfj.getPreferredSize
                val res = pfc.peer.getBaseline(d.width, d.height)
                res + pfc.peer.getY
              }
            }
            val l = new javax.swing.BoxLayout(p, Orientation.Vertical.id)
            p.setLayout(l)
            p
          }

          contents += pfc
          contents += fb // fmc
        }

        def updateFormat(): Unit =
          pfc.valueOption match {
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

        updateFormat()

        pfc.reactions += {
          case ValueChanged(_) => updateFormat()
        }

        component = c
      }
      super.init()
    }
  }

  private final case class Impl() extends AudioFileIn with ComponentImpl { w =>
    override def productPrefix: String = "AudioFileIn" // serialization

    private[this] val pf = {
      val res   = PathField()
      res.title = "Select Audio Input File"
      res
    }

//    private[this] val fmt = {
//      val res       = TextField(12)
////      res.editable  = false
////      res.focusable = false
//      res
//    }

    def pathField   : PathField = pf
//    def formatField : TextField = fmt

    protected def mkControl[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): Repr[S] =
      new Expanded[S](this).init()

    def value: Model[File] = pf.value

    def title: Ex[String] = pf.title

    def title_=(value: Ex[String]): Unit = pf.title = value
  }
}
trait AudioFileIn extends Component {
  type C = scala.swing.Panel

//  def pathField   : PathField
//  def formatField : TextField

  var title : Ex[String]
  def value : Model[File]
}
