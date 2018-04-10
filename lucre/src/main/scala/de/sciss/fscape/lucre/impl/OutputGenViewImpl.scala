/*
 *  OutputGenViewImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

import de.sciss.fscape.lucre.FScape.{Output, Rendering}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.synth.proc.{GenContext, GenView}

import scala.util.Try

object OutputGenViewImpl {
  def apply[S <: Sys[S]](config: Control.Config, output: Output[S], rendering: Rendering[S])
                        (implicit tx: S#Tx, context: GenContext[S]): OutputGenView[S] = {
    new Impl(config, tx.newHandle(output), key = output.key, valueType = output.valueType,
      rendering = rendering).init()
  }

  private final class Impl[S <: Sys[S]](config: Control.Config,
                                        outputH: stm.Source[S#Tx, Output[S]],
                                        val key: String,
                                        val valueType: Obj.Type,
                                        rendering: Rendering[S])
                                       (implicit context: GenContext[S])
    extends OutputGenView[S] with ObservableImpl[S, GenView.State] {
    view =>

    private[this] var observer: Disposable[S#Tx] = _

    def typeId: Int = Output.typeId

    def state(implicit tx: S#Tx): GenView.State = rendering.state

    def output(implicit tx: S#Tx): Output[S] = outputH()

    def reactNow(fun: S#Tx => GenView.State => Unit)(implicit tx: S#Tx): Disposable[S#Tx] = {
      val res = react(fun)
      fun(tx)(state)
      res
    }

    def value(implicit tx: S#Tx): Option[Try[Obj[S]]] = rendering.outputResult(this)

    private def fscape(implicit tx: S#Tx): FScape[S] = outputH().fscape

    def init()(implicit tx: S#Tx): this.type = {
      observer = rendering.react { implicit tx => upd =>
        fire(upd)
      }
      this
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      observer.dispose()
      val _fscape = fscape
      context.release(_fscape)
    }
  }
}