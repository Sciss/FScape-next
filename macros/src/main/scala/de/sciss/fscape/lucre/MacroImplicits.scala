package de.sciss.fscape.lucre

import de.sciss.lucre.stm.Sys
import de.sciss.fscape.lucre.impl.Macros

import scala.language.experimental.macros

/** Enables implicits extensions
  * to assign `Graph`s to an `FScape` object from a standard IDE,
  * compiling these objects correctly for storage in the workspace,
  * and preserving the corresponding source code.
  */
object MacroImplicits {
  implicit final class FScapeMacroOps[S <: Sys[S]](/* private[lucre] */ val `this`: FScape[S]) extends AnyVal {
    def setGraph(body: Unit)(implicit tx: S#Tx): Unit =
      macro Macros.fscapeGraphWithSource[S]
  }
}
