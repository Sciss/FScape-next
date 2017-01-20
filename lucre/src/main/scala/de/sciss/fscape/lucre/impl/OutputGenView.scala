/*
 *  OutputGenView.scala
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

import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.stream.Control
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.synth.proc.{GenContext, GenView}

import scala.util.{Failure, Success, Try}

final class OutputGenView[S <: Sys[S]](config: Control.Config,
                                       outputH: stm.Source[S#Tx, Output[S]],
                                       val valueType: Obj.Type,
                                       fscView: FScapeView[S])
                                      (implicit context: GenContext[S])
  extends GenView[S] with ObservableImpl[S, GenView.State] { view =>

  private[this] var observer: Disposable[S#Tx] = _

  def typeID: Int = Output.typeID

  def state(implicit tx: S#Tx): GenView.State = fscView.state

  def reactNow(fun: S#Tx => GenView.State => Unit)(implicit tx: S#Tx): Disposable[S#Tx] = {
    val res = react(fun)
    fun(tx)(state)
    res
  }

  def value(implicit tx: S#Tx): Option[Try[Obj[S]]] = fscView.result match {
    case Some(Success(_)) =>
      outputH() match {
        case oi: OutputImpl[S] => oi.value.map(v => Success(v))
        case _ => None
      }
    case res @ Some(Failure(_)) =>
      res.asInstanceOf[Option[Try[Obj[S]]]]

    case None => None
  }

  private def fscape(implicit tx: S#Tx): FScape[S] = outputH().fscape

  def init()(implicit tx: S#Tx): this.type = {
    observer = fscView.react { implicit tx => upd =>
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