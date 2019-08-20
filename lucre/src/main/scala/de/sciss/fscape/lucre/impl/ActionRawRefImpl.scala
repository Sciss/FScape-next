/*
 *  ActionRefImpl.scala
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

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.{SoundProcesses, Universe}

final class ActionRawRefImpl[S <: Sys[S]](val key: String,
                                          fH: stm.Source[S#Tx, FScape[S]], aH: stm.Source[S#Tx, proc.ActionRaw[S]])
                                         (implicit universe: Universe[S])
  extends Input.Action.Value {

  def execute(value: Any): Unit = {
    import universe.cursor
    SoundProcesses.atomic[S, Unit] { implicit tx =>
      val f = fH()
      val a = aH()
      val u = proc.ActionRaw.Universe(self = a, invoker = Some(f), value = value)
      a.execute(u)
    }
  }
}