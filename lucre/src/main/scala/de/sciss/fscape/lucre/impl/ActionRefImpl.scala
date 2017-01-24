/*
 *  ActionRefImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.{SoundProcesses, WorkspaceHandle}

final class ActionRefImpl[S <: Sys[S]](val key: String,
                                       fH: stm.Source[S#Tx, FScape[S]], aH: stm.Source[S#Tx, proc.Action[S]])
                                      (implicit cursor: stm.Cursor[S], workspace: WorkspaceHandle[S])
  extends Input.Action.Value {

  def execute(value: Any): Unit = SoundProcesses.atomic[S, Unit] { implicit tx =>
    val f = fH()
    val a = aH()
    val u = proc.Action.Universe(self = a, workspace = workspace, invoker = Some(f), value = value)
    a.execute(u)
  }
}