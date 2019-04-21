/*
 *  FileInExpandedImpl.scala
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

package de.sciss.lucre.swing.graph.impl

import de.sciss.desktop
import de.sciss.desktop.TextFieldWithPaint
import de.sciss.file.File
import de.sciss.lucre.expr.Ex
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.{AudioFileIn, PathField}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{PanelWithPathField, View, deferTx}

import scala.swing.event.ValueChanged
import scala.swing.{Orientation, SequentialContainer, Swing}
import scala.util.control.NonFatal

trait FileInExpandedImpl[S <: Sys[S]]
  extends View[S] with ComponentHolder[PanelWithPathField] with ComponentExpandedImpl[S] {

  type C = PanelWithPathField

  protected def mkFormat(f: File): String

  override def initComponent()(implicit tx: S#Tx, ctx: Ex.Context[S]): this.type = {
    val valueOpt  = ctx.getProperty[Ex[File   ]](w, PathField.keyValue).map(_.expand[S].value)
    val titleOpt  = ctx.getProperty[Ex[String ]](w, PathField.keyTitle).map(_.expand[S].value)
    val pathVis   = ctx.getProperty[Ex[Boolean]](w, AudioFileIn.keyPathFieldVisible).fold(
      AudioFileIn.defaultPathFieldVisible)(_.expand[S].value)
    val fmtVis    = ctx.getProperty[Ex[Boolean]](w, AudioFileIn.keyFormatVisible).fold(
      AudioFileIn.defaultFormatVisible)(_.expand[S].value)

    deferTx {
      val c: C = new PanelWithPathField with SequentialContainer.Wrapper  {
        private[this] lazy val fmc = {
          val res = new TextFieldWithPaint(27)
          res.editable  = false
          res.focusable = false
          res
        }

        override def enabled_=(b: Boolean): Unit = {
          super.enabled_=(b)
          if (pathVis ) pathField .enabled = b
          if (fmtVis  ) fmc       .enabled = b
        }

        def updateFormat(): Unit =
          pathField.valueOption match {
            case Some(f) =>
              try {
                val fmtS = mkFormat(f)
                if (fmtVis) fmc.text = fmtS
                pathField.paint = None
              } catch {
                case NonFatal(ex) =>
                  if (fmtVis) fmc.text = ex.toString
                  pathField.paint = Some(TextFieldWithPaint.RedOverlay)
              }
            case None =>
              if (fmtVis) fmc.text = ""
              pathField.paint = None
          }

        lazy val pathField: desktop.PathField = {
          val res = new desktop.PathField
          valueOpt.foreach(res.value = _)
          titleOpt.foreach(res.title = _)
          res.listenTo(res)
          res.reactions += {
            case ValueChanged(_) => updateFormat()
          }
          res
        }

        if (pathVis) updateFormat()

        private[this] lazy val fb = {
          val res = new scala.swing.BoxPanel(Orientation.Horizontal)
          res.contents += fmc
          if (pathVis) {
            res.contents += Swing.HStrut(pathField.button.preferredSize.width)
          }
          res
        }

        override lazy val peer: javax.swing.JPanel = {
          val p = new javax.swing.JPanel with SuperMixin {
            override def getBaseline(width: Int, height: Int): Int =
              if (!pathVis) super.getBaseline(width, height) else {
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

        if (pathVis ) contents += pathField
        if (fmtVis  ) contents += fb
      }

      component = c
    }
    super.initComponent()
  }
}