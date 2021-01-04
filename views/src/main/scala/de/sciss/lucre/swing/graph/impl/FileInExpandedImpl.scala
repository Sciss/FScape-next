/*
 *  FileInExpandedImpl.scala
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

package de.sciss.lucre.swing.graph.impl

import java.io.File
import java.net.URI

import de.sciss.desktop
import de.sciss.desktop.TextFieldWithPaint
import de.sciss.lucre.Txn
import de.sciss.lucre.expr.Context
import de.sciss.lucre.expr.graph.Ex
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.graph.{AudioFileIn, PathField}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{PanelWithPathField, View}

import scala.swing.event.ValueChanged
import scala.swing.{Orientation, SequentialContainer, Swing}
import scala.util.Try
import scala.util.control.NonFatal

trait FileInExpandedImpl[T <: Txn[T]]
  extends View[T] with ComponentHolder[PanelWithPathField] with ComponentExpandedImpl[T] {

  type C = PanelWithPathField

  protected def mkFormat(f: URI): String

  override def initComponent()(implicit tx: T, ctx: Context[T]): this.type = {
    val valueOpt  = ctx.getProperty[Ex[URI    ]](peer, PathField.keyValue).map(_.expand[T].value)
    val titleOpt  = ctx.getProperty[Ex[String ]](peer, PathField.keyTitle).map(_.expand[T].value)
    val pathVis   = ctx.getProperty[Ex[Boolean]](peer, AudioFileIn.keyPathFieldVisible).fold(
      AudioFileIn.defaultPathFieldVisible)(_.expand[T].value)
    val fmtVis    = ctx.getProperty[Ex[Boolean]](peer, AudioFileIn.keyFormatVisible).fold(
      AudioFileIn.defaultFormatVisible)(_.expand[T].value)

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
                val uri   = f.toURI
                val fmtS  = mkFormat(uri)
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
          valueOpt.foreach { uri =>
            val fileOpt     = if (!uri.isAbsolute) None else Try(new File(uri)).toOption
            res.valueOption = fileOpt
          }
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