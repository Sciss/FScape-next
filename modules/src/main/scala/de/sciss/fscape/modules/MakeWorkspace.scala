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
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.synth.proc.{Durable, SoundProcesses, Widget, Workspace}

object MakeWorkspace {
  type S = Durable

  def main(args: Array[String]): Unit = {
    run()
  }

  def run(): Unit = {
    SoundProcesses.init()
    FScape        .init()
    Widget        .init()

    val modules = Seq(
      ChangeGainModule, LimiterModule, TapeSpeedModule
    )

    val dir = userHome / "mellite" / "sessions" / "FScape-modules.mllt"
    require (!dir.exists())
    val ds  = BerkeleyDB.factory(dir)
    val ws  = Workspace.Durable.empty(dir, ds)
    ws.cursor.step { implicit tx =>
      val r = ws.root
      modules.foreach { m =>
        m.add(r)
      }
      ws.dispose()
    }
  }
}
