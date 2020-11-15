/*
 *  MacroImplicits.scala
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

package de.sciss.fscape.lucre

import de.sciss.fscape.lucre.impl.Macros
import de.sciss.lucre.Txn
import de.sciss.synth.proc.FScape

import scala.language.experimental.macros

/** Enables implicits extensions
  * to assign `Graph`s to an `FScape` object from a standard IDE,
  * compiling these objects correctly for storage in the workspace,
  * and preserving the corresponding source code.
  */
object MacroImplicits {
  implicit final class FScapeMacroOps[T <: Txn[T]](/* private[lucre] */ val `this`: FScape[T]) extends AnyVal {
    def setGraph(body: Unit)(implicit tx: T): Unit =
      macro Macros.fscapeGraphWithSource[T]
  }
}
