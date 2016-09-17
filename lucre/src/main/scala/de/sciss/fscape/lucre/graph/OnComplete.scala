/*
 *  OnComplete.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package graph

import de.sciss.fscape.lucre.UGenGraphBuilder.ActionRef
import de.sciss.fscape.stream
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object OnComplete {
  final case class WithRef(ref: ActionRef) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit = makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, rest = ref)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit =
      lucre.stream.OnComplete(ref)

    override def productPrefix: String = classOf[WithRef].getName
  }
}
/** A UGen that invokes an action once the surrounding graph has completed.
  * The action is called with a `Try[Unit]` as its universe `value`.
  *
  * @param key  key to the hosting object's attribute map, at which an
  *             action is expected to be found.
  */
final case class OnComplete(key: String) extends Lazy.Expander[Unit] {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub  = UGenGraphBuilder.get(b)
    val ref = ub.requestAction(key).getOrElse(sys.error(s"Missing Attribute $key"))
    OnComplete.WithRef(ref)
  }
}