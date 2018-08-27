/*
 *  BlockingGraphStage.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.{ActorAttributes, Attributes, Shape}

/** Overrides dispatcher to implement async boundary. */
abstract class BlockingGraphStage[S <: Shape](name: String)(implicit ctrl: Control)
  extends StageImpl[S](name) {

  override def initialAttributes: Attributes =
    if (ctrl.config.useAsync) {
      Attributes.name(toString) and
      ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")
    } else {
      Attributes.name(toString)
    }
}
