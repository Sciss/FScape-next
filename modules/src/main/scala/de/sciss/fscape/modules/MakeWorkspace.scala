/*
 *  MakeWorkspace.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.lucre.store.BerkeleyDB
import de.sciss.lucre.{BooleanObj, Folder, Txn}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Markdown, SoundProcesses, Widget, Workspace}
import org.rogach.scallop.{ScallopConf, ScallopOption => Opt}

import scala.io.Source

object MakeWorkspace {
  final case class Config(modules: List[Module] = list, target: File = file("fscape.mllt"))

  def main(args: Array[String]): Unit = {
    object parse extends ScallopConf(args) {
      printedName = "fscape-modules"
      version(printedName)
      val target: Opt[File] = trailArg(descr = "Target .mllt Mellite workspace.")
      verify()
      val config: Config = Config(target = target())
    }

    run(parse.config)
  }

  val list: List[Module] =
    List(
      ModChangeGain,
      ModLimiter,
      ModTapeSpeed,
      ModFourierTranslation,
      ModMakeLoop,
      ModSignalGenerator,
      ModFreqShift,
      ModSincFilter,
      ModMixToMono,
      ModRemoveDC,
      ModInverseFilter,
      ModBleach,
      ModSpectralShadow,
      ModSlewRateLimiter,
    ).sortBy(_.name)

  def help[T <: Txn[T]](m: Module)(implicit tx: T): Option[Markdown[T]] = {
    val clz = m.getClass
    val n0  = clz.getName
    val n1  = if (n0.endsWith("$")) n0.dropRight(1) else n0
    val n   = n1.substring(n1.lastIndexOf(".") + 1)
    val nm  = s"$n.md"
    Option(clz.getResourceAsStream(nm)).map { is =>
      val text  = Source.fromInputStream(is, "UTF-8").mkString
      val res   = Markdown.newVar[T](text)
      res.name  = s"${m.name} Help"
      res.attr.put(Markdown.attrEditMode, BooleanObj.newVar[T](false))
      res
    }
  }

  def add[T <: Txn[T]](f: Folder[T], m: Module)(implicit tx: T): Unit = {
    val fsc   = m.apply[T]()
    fsc.name  = m.name
    val w     = m.ui[T]()
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

    require (!target.exists(), s"Workspace '${target.name}' already exists. Not overwriting.")
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
