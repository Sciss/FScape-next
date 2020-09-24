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
import de.sciss.lucre.Txn
import de.sciss.synth.proc.{GenContext, GenView}

object OutputGenView {
  def apply[T <: Txn[T]](config: Control.Config, output: Output[T], rendering: Rendering[T])
                        (implicit tx: T, context: GenContext[T]): OutputGenView[T] =
    Impl(config, output, rendering)
}
trait OutputGenView[T <: Txn[T]] extends GenView[T] {
  def output(implicit tx: T): Output[T]
}
