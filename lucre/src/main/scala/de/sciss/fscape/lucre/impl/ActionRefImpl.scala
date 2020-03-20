/*
 *  ActionRefImpl.scala
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

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.lucre.expr.SourcesAsRunnerMap
import de.sciss.lucre.expr.graph.Const
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.{Runner, SoundProcesses, Universe}

final class ActionRefImpl[S <: Sys[S]](val key: String,
                                       fH: stm.Source[S#Tx, FScape[S]], aH: stm.Source[S#Tx, stm.Obj[S]])
                                      (implicit universe: Universe[S])
  extends Input.Action.Value {

  def execute(value: Option[Any]): Unit = {
    import universe.cursor
    SoundProcesses.step[S](s"FScape Action($key)") { implicit tx: S#Tx =>
      val a   = aH()
      val r   = Runner(a)
      val m0: SourcesAsRunnerMap.Map[S] = Map("invoker" -> Left(fH))
      val m1  = value match {
        case Some(v)  => m0 + ("value" -> Right(new Const.Expanded[S, Any](v)))
        case None     => m0
      }
      r.prepare(new SourcesAsRunnerMap[S](m1))
      r.runAndDispose()
    }
  }
}