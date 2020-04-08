/*
 *  FilterWindowedInAOutA.scala
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

package de.sciss.fscape.stream.impl.logic

import akka.stream.{Inlet, Outlet, Shape}
import de.sciss.fscape.stream.{BufElem, Control, Layer, StreamType}
import de.sciss.fscape.stream.impl.Handlers
import de.sciss.fscape.stream.impl.Handlers.{InMain, OutMain}

/** An abstract class implementing both `Handlers` and `WindowedInAOutA`. This solves a bug in Scala
  * specialization where we cannot call `InMain` inside a specialized class. In this case,
  * `FilterWindowedInAOutA` is not specialized itself, but classes extending `FilterWindowedInAOutA`
  * may well choose to do so.
  */
abstract class FilterWindowedInAOutA[A, E <: BufElem[A], S <: Shape](name: String, layer: Layer, shape: S)
                                                                    (inlet: Inlet[E], outlet: Outlet[E])
                                                                    (implicit control: Control,
                                                                     protected val tpe: StreamType[A, E])
  extends Handlers[S](name, layer, shape) with WindowedInAOutA[A, E] {

  protected final val hIn : InMain [A, E] = InMain [A, E](this, inlet )
  protected final val hOut: OutMain[A, E] = OutMain[A, E](this, outlet)
}
