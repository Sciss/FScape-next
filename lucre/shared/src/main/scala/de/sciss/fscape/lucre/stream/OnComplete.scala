/*
 *  OnComplete.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre.stream

import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream.Builder

import scala.util.{Failure, Success}

object OnComplete {
  def apply(ref: Input.Action.Value)(implicit b: Builder): Unit = {
    val ctrl = b.control
    import ctrl.config.executionContext
    ctrl.status.onComplete { tr =>
      val message = tr match {
        case Success(_)   => None
        case Failure(ex)  => Some(s"${ex.getClass.getSimpleName} - ${ex.getMessage}")
      }
      ref.execute(message)
    }
  }
}