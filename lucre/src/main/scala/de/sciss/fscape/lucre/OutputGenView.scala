/*
 *  OutputGenView.scala
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

import de.sciss.fscape.lucre.FScape.{Output, Rendering}
import de.sciss.fscape.lucre.impl.{OutputGenViewImpl => Impl}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.{GenContext, GenView}

object OutputGenView {
  def apply[S <: Sys[S]](config: Control.Config, output: Output[S], rendering: Rendering[S])
                        (implicit tx: S#Tx, context: GenContext[S]): OutputGenView[S] =
    Impl(config, output, rendering)
}
trait OutputGenView[S <: Sys[S]] extends GenView[S] {
  def output(implicit tx: S#Tx): Output[S]
}
