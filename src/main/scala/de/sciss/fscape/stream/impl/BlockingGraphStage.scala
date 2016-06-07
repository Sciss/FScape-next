/*
 *  BlockingGraphStage.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.stage.GraphStage
import akka.stream.{ActorAttributes, Attributes, Shape}

/** Overrides dispatcher to implement async boundary. */
abstract class BlockingGraphStage[S <: Shape] extends GraphStage[S] {
  protected def ctrl: Control

  override def initialAttributes: Attributes =
    if (ctrl.config.useAsync) {
      Attributes.name(toString) and
      ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")
    } else {
      Attributes.name(toString)
    }
}
