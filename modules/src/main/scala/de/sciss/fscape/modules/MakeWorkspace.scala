package de.sciss.fscape.modules

import de.sciss.synth.proc.{Durable, SoundProcesses, Widget, Workspace}
import de.sciss.file._
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.expr.BooleanObj
import de.sciss.lucre.stm.store.BerkeleyDB

object MakeWorkspace {
  type S = Durable

  def main(args: Array[String]): Unit = {
    run()
  }

  def run(): Unit = {
    SoundProcesses.init()
    FScape        .init()
    Widget        .init()

    val dir = userHome / "mellite" / "sessions" / "FScape-modules.mllt"
    require (!dir.exists())
    val ds  = BerkeleyDB.factory(dir)
    val ws  = Workspace.Durable.empty(dir, ds)
    ws.cursor.step { implicit tx =>
      val r   = ws.root
      val fsc = ChangeGain    [S]()
      val ui  = ChangeGain.ui [S]()
      ui.attr.put("run"       , fsc)
      ui.attr.put("edit-mode" , BooleanObj.newVar(false))
      r.addLast(fsc)
      r.addLast(ui)
      ws.dispose()
    }
  }
}
