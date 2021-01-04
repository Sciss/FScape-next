/*
 *  Module.scala
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

package de.sciss.fscape.modules

import de.sciss.lucre.Txn
import de.sciss.proc.{FScape, Widget}

trait Module {
  // ---- abstract ----

  def name: String

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T]

  def ui[T <: Txn[T]]()(implicit tx: T): Widget[T]
}
