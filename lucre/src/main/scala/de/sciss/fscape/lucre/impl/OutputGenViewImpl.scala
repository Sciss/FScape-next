/*
 *  OutputGenViewImpl.scala
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

import de.sciss.fscape.lucre.FScape.{Output, Rendering}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.impl.ObservableImpl
import de.sciss.lucre.{Disposable, Obj, Source, Txn}
import de.sciss.synth.proc.{GenContext, GenView}

import scala.util.Try

object OutputGenViewImpl {
  def apply[T <: Txn[T]](config: Control.Config, output: Output[T], rendering: Rendering[T])
                        (implicit tx: T, context: GenContext[T]): OutputGenView[T] = {
    new Impl(config, tx.newHandle(output), key = output.key, valueType = output.valueType,
      rendering = rendering).init()
  }

  private final class Impl[T <: Txn[T]](config: Control.Config,
                                        outputH: Source[T, Output[T]],
                                        val key: String,
                                        val valueType: Obj.Type,
                                        rendering: Rendering[T])
                                       (implicit context: GenContext[T])
    extends OutputGenView[T] with ObservableImpl[T, GenView.State] {
    view =>

    private[this] var observer: Disposable[T] = _

    def typeId: Int = Output.typeId

    def state(implicit tx: T): GenView.State = rendering.state

    def output(implicit tx: T): Output[T] = outputH()

    def reactNow(fun: T => GenView.State => Unit)(implicit tx: T): Disposable[T] = {
      val res = react(fun)
      fun(tx)(state)
      res
    }

    def value(implicit tx: T): Option[Try[Obj[T]]] = rendering.outputResult(this)

    private def fscape(implicit tx: T): FScape[T] = outputH().fscape

    def init()(implicit tx: T): this.type = {
      observer = rendering.react { implicit tx => upd =>
        fire(upd)
      }
      this
    }

    def dispose()(implicit tx: T): Unit = {
      observer.dispose()
      val _fscape = fscape
      context.release(_fscape)
    }
  }
}