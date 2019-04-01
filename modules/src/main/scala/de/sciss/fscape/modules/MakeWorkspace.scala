/*
 *  MakeWorkspace.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.modules

import de.sciss.file._
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.expr.BooleanObj
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.lucre.stm.{Folder, Sys}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Durable, Markdown, SoundProcesses, Widget, Workspace}

object MakeWorkspace {
  type S = Durable

  final case class Config(modules: List[Module], target: File)

  def main(args: Array[String]): Unit = {
    val c = Config(
      modules = list,
      target  = userHome / "mellite" / "sessions" / "FScape-modules.mllt"
    )
    run(c)
  }

  val list: List[Module] =
    List(
      ModChangeGain, ModLimiter, ModTapeSpeed, ModFourierTranslation, ModMakeLoop
    ).sortBy(_.name)

  def help[S <: Sys[S]](m: Module)(implicit tx: S#Tx): Option[Markdown[S]] = {
    val clz = m.getClass
    val n0  = clz.getName
    val n1  = if (n0.endsWith("$")) n0.dropRight(1) else n0
    val n   = n1.substring(n1.lastIndexOf(".") + 1)
    val nm  = s"$n.md"
    Option(clz.getResourceAsStream(nm)).map { is =>
      val text  = io.Source.fromInputStream(is, "UTF-8").mkString
      val res   = Markdown.newVar[S](text)
      res.name  = s"${m.name} Help"
      res.attr.put(Markdown.attrEditMode, BooleanObj.newVar[S](false))
      res
    }
  }

  def add[S <: Sys[S]](f: Folder[S], m: Module)(implicit tx: S#Tx): Unit = {
    val fsc   = m.apply[S]()
    fsc.name  = m.name
    val w     = m.ui[S]()
    w.name    = m.name
    w.attr.put("run"       , fsc)
    w.attr.put("edit-mode" , BooleanObj.newVar(false))
    f.addLast(w)
    // f.addLast(fsc)
    val hOpt  = help(m)
    hOpt.fold[Unit] {
      tx.afterCommit {
        println(s"Warning: No help for '${m.name}'")
      }
    } { help =>
      w.attr.put("help", help)
      f.addLast(help)
    }
  }

  def run(config: Config): Unit = {
    import config._

    SoundProcesses.init()
    FScape        .init()
    Widget        .init()

    require (!target.exists(), s"Workspace '${target.name}' already exists.")
    val ds  = BerkeleyDB.factory(target)
    val ws  = Workspace.Durable.empty(target, ds)
    ws.cursor.step { implicit tx =>
      val r = ws.root
      modules.foreach { m =>
        add(r, m)
      }
      ws.dispose()
    }
  }
}
