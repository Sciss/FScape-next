/*
 *  Module.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.modules

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.expr.BooleanObj
import de.sciss.lucre.stm.{Folder, Sys}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Widget

trait Module {
  // ---- abstract ----

  def name: String

  def apply[S <: Sys[S]]()(implicit tx: S#Tx): FScape[S]

  def ui[S <: Sys[S]]()(implicit tx: S#Tx): Widget[S]

  // ---- impl ----

  def add[S <: Sys[S]](f: Folder[S])(implicit tx: S#Tx): Unit = {
    val fsc   = apply[S]()
    fsc.name  = name
    val w     = ui[S]()
    w.name    = name
    w.attr.put("run"       , fsc)
    w.attr.put("edit-mode" , BooleanObj.newVar(false))
    f.addLast(fsc)
    f.addLast(w)
  }
}
