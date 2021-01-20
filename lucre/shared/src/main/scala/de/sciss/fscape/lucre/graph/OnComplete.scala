/*
 *  OnComplete.scala
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

package de.sciss.fscape
package lucre
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object OnComplete extends ProductReader[OnComplete] {
  final case class WithRef private(ref: Input.Action.Value) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit = makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, args, adjuncts = Adjunct.String(ref.key) :: Nil)
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit =
      lucre.stream.OnComplete(ref)

    override def productPrefix: String = s"OnComplete$$WithRef"
  }

  override def read(in: RefMapIn, key: String, arity: Int): OnComplete = {
    require (arity == 1)
    val _key = in.readString()
    new OnComplete(_key)
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
    val ref = ub.requestInput(Input.Action(key)) // .getOrElse(sys.error(s"Missing Attribute $key"))
    OnComplete.WithRef(ref)
    ()
  }
}