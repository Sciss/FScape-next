/*
 *  Macros.scala
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
package impl

import de.sciss.fscape.Graph
import de.sciss.lucre.Txn
import de.sciss.synth.proc.{Code, FScape}
import de.sciss.synth.proc.impl.Macros.mkSource

import scala.reflect.macros.blackbox

object Macros {
  def fscapeGraphWithSource[T <: Txn[T]](c: blackbox.Context)(body: c.Expr[Unit])(tx: c.Expr[T])
                                        (implicit tt: c.WeakTypeTag[T]): c.Expr[Unit] = {
    import c.universe._

    val source      = mkSource(c)("fscape", body.tree)
    val sourceExpr  = c.Expr[String](Literal(Constant(source)))
    reify {
      val ext           = c.prefix.splice.asInstanceOf[MacroImplicits.FScapeMacroOps[T]]
      implicit val txc  = tx.splice // N.B.: don't annotate the type with `S#Tx`, it will break scalac
      val p             = ext.`this`
      p.graph()         = FScape.GraphObj.newConst[T](Graph(body.splice))
      val code          = FScape.Code(sourceExpr.splice)
      val codeObj       = Code.Obj.newVar[T](Code.Obj.newConst[T](code))
      p.attr.put(FScape.attrSource, codeObj)
      ()
    }
  }
}