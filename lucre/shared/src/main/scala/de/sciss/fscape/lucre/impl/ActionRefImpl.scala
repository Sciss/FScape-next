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
import de.sciss.lucre.{Obj, Source, Txn}
import de.sciss.synth.proc.{FScape, Runner, SoundProcesses, Universe}

final class ActionRefImpl[T <: Txn[T]](val key: String,
                                       fH: Source[T, FScape[T]], aH: Source[T, Obj[T]])
                                      (implicit universe: Universe[T])
  extends Input.Action.Value {

  def execute(value: Option[Any]): Unit = {
    import universe.cursor
    SoundProcesses.step[T](s"FScape Action($key)") { implicit tx: T =>
      val a   = aH()
      val r   = Runner(a)
      val m0: SourcesAsRunnerMap.Map[T] = Map("invoker" -> Left(fH))
      val m1  = value match {
        case Some(v)  => m0 + ("value" -> Right(new Const.Expanded[T, Any](v)))
        case None     => m0
      }
      r.prepare(new SourcesAsRunnerMap[T](m1))
      r.runAndDispose()
    }
  }
}