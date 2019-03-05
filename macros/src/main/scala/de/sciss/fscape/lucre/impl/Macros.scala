/*
 *  Macros.scala
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

package de.sciss.fscape.lucre
package impl

import de.sciss.fscape.Graph
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Code
import de.sciss.synth.proc.impl.Macros.mkSource

import scala.reflect.macros.blackbox

object Macros {
  def fscapeGraphWithSource[S <: Sys[S]](c: blackbox.Context)(body: c.Expr[Unit])(tx: c.Expr[S#Tx])
                                        (implicit tt: c.WeakTypeTag[S]): c.Expr[Unit] = {
    import c.universe._

    val source      = mkSource(c)("proc", body.tree)
    val sourceExpr  = c.Expr[String](Literal(Constant(source)))
    reify {
      val ext           = c.prefix.splice.asInstanceOf[MacroImplicits.FScapeMacroOps[S]]
      implicit val txc  = tx.splice // don't bloody annotate the type with `S#Tx`, it will break scalac
      val p             = ext.`this`
      p.graph()         = GraphObj.newConst[S](Graph(body.splice))
      val code          = FScape.Code(sourceExpr.splice)
      val codeObj       = Code.Obj.newVar[S](Code.Obj.newConst[S](code))
      p.attr.put(FScape.attrSource, codeObj)
    }
  }
}