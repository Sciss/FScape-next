/*
 *  Action.scala
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
package lucre.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object Action extends ProductReader[Action] {
  final case class WithRef private(action: Action, ref: Input.Action.Value) extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, Vector(action.trig.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, args, adjuncts = Adjunct.String(ref.key) :: Nil)
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val Vec(trig) = args
      lucre.stream.Action(gate = trig.toInt, ref = ref)
    }

    override def productPrefix: String = s"Action$$WithRef"
  }

  override def read(in: RefMapIn, key: String, arity: Int): Action = {
    require (arity == 2)
    val _trig = in.readGE()
    val _key  = in.readString()
    new Action(_trig, _key)
  }
}
/** A graph element that executes an action upon receiving a trigger.
  *
  * @param trig   the trigger input signal
  * @param key    a key into the process' attribute map. the value peer stored
  *               at that location should be of type `proc.Action`
  */
final case class Action(trig: GE, key: String) extends Lazy.Expander[Unit] {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub  = UGenGraphBuilder.get(b)
    val ref = ub.requestInput(Input.Action(key)) // .getOrElse(sys.error(s"Missing Attribute $key"))
    Action.WithRef(this, ref)
    ()
  }
}

///** A graph element that executes an action upon receiving a trigger,
//  * sampling the values at that moment and making them available
//  * in the action through the `values` method.
//  *
//  * @param trig   the trigger input signal
//  * @param in     the input signal to sample and pass on to the action
//  * @param key    a key into the process' attribute map. the value peer stored
//  *               at that location should be of type `proc.Action`
//  */
//final case class Reaction(trig: GE, in: GE, key: String) extends Lazy.Expander[Unit] {
//  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
//    val ub = UGenGraphBuilder.get(b)
//    val ar = ub.requestAction(key).getOrElse(sys.error(s"Missing Attribute $key"))
////    ub.requestInput(Input.Action(key))
////    impl.ActionResponder.makeUGen(trig, Some(Flatten(in)), key)
//      ...
//  }
//}
