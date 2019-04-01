package de.sciss.lucre.swing

import de.sciss.desktop.UndoManager
import de.sciss.file.File
import de.sciss.lucre.expr
import de.sciss.lucre.stm.Durable
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.submin.Submin

import scala.swing.{Component, Frame, MainFrame, Menu, MenuBar, MenuItem, SimpleSwingApplication}

trait AppLike extends SimpleSwingApplication {
  type S = Durable
  implicit val system: S = Durable(BerkeleyDB.factory(File.createTemp(directory = true)))

  implicit lazy val undo: UndoManager = UndoManager()

  override def main(args: Array[String]): Unit = {
    expr.init()
    Submin.install(true)
    super.main(args)
  }

  protected def mkView(): Component

  lazy val top: Frame = {
    val mb = new MenuBar {
      contents += new Menu("Edit") {
        contents += new MenuItem(undo.undoAction)
        contents += new MenuItem(undo.redoAction)
      }
    }

    val res = new MainFrame {
      title     = "LucreSwing"
      contents  = mkView()
      menuBar   = mb
      pack().centerOnScreen()
      open()
    }
    res
  }
}