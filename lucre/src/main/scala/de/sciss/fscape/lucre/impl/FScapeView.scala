/*
 *  FScapeView.scala
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

import de.sciss.fscape.lucre.FScape.Rendering
import de.sciss.fscape.stream.Control
import de.sciss.lucre.stm.{Disposable, Sys}
import de.sciss.synth.proc.{GenContext, GenView}

import scala.concurrent.stm.Ref
import scala.util.{Success, Try}

object FScapeView {
  def apply[S <: Sys[S]](peer: FScape[S], config: Control.Config)
                        (implicit tx: S#Tx, context: GenContext[S]): FScapeView[S] = {
    new Impl[S](context, config).init(peer)
  }

  private val successUnit = Success(())

  private final class Impl[S <: Sys[S]](context: GenContext[S], config: Control.Config)
    extends FScapeView[S] {

    implicit val control: Control = Control(config)

    private[this] val _state      = Ref[(GenView.State, Try[Unit])]((GenView.Stopped, successUnit))
    private[this] val _rendering  = Ref(Option.empty[(Rendering[S], Disposable[S#Tx])])

    def init(peer: FScape[S])(implicit tx: S#Tx): this.type = {
      val graph = peer.graph.value
      import context.{cursor, workspaceHandle}
      val state = UGenGraphBuilder.build(peer, graph)
      state match {
        case c: UGenGraphBuilder.Complete[S] =>
          // - if there are no outputs, we're done
          // - otherwise check structure:
          val str = c.structure
          // - check file cache for structure

          // c.graph

        case _ =>
          ???
      }
      ???
      this
    }

    def dispose()(implicit tx: S#Tx): Unit = ???
  }
}
trait FScapeView[S <: Sys[S]] extends Disposable[S#Tx] {

}