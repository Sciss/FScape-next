/*
 *  HPF.scala
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
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{HPF_LPF_Impl, NodeImpl, StageImpl}

object HPF {
  def apply(in: OutD, freqN: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(freqN , stage.in1)
    stage.out
  }

  private final val name = "HPF"

  private type Shp = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.in"    ),
      in1 = InD (s"$name.freqN" ),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new HPF_LPF_Impl(shape, layer, name, isHPF = true)
  }
}