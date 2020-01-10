/*
 *  UGenSource.scala
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

import de.sciss.fscape.graph.UGenInGroup
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object UGenSource {
  trait ZeroOut   extends UGenSource[Unit, Unit]
  trait SingleOut extends SomeOut[StreamOut]
  trait MultiOut  extends SomeOut[Vec[StreamOut]]

  protected sealed trait SomeOut[S] extends UGenSource[UGenInLike, S] with GE.Lazy

  def unwrap[S](source: UGenSource.SomeOut[S], args: Vec[UGenInLike])(implicit b: UGenGraph.Builder): UGenInLike = {
    var uIns    = Vector.empty: Vec[UGenIn]
    var uInsOk  = true
    var exp     = 0
    args.foreach(_.unbubble match {
      case u: UGenIn => if (uInsOk) uIns :+= u
      case g: UGenInGroup =>
        exp     = math.max(exp, g.numOutputs)
        uInsOk  = false // don't bother adding further UGenIns to uIns
    })
    if (uInsOk) {
      // aka uIns.size == args.size
      source.makeUGen(uIns)
    } else {
      // rewrap(args, exp)
      UGenInGroup(Vector.tabulate(exp)(i => unwrap(source, args.map(_.unwrap(i)))))
    }
  }

  def unwrap(source: UGenSource.ZeroOut, args: Vec[UGenInLike])(implicit b: UGenGraph.Builder): Unit = {
    var uIns    = Vector.empty: Vec[UGenIn]
    var uInsOk  = true
    var exp     = 0
    args.foreach(_.unbubble match {
      case u: UGenIn => if (uInsOk) uIns :+= u
      case g: UGenInGroup =>
        exp     = math.max(exp, g.numOutputs)
        uInsOk  = false // don't bother adding further UGenIns to uIns
    })
    if (uInsOk) {
      // aka uIns.size == args.size
      source.makeUGen(uIns)
    } else {
      // rewrap(args, exp)
      var i = 0
      while (i < exp) {
        unwrap(source, args.map(_.unwrap(i)))
        i += 1
      }
    }
  }

  /** Simple forwarder to `in.expand` that can be used to access
    * the otherwise package-private method.
    */
  def expand(in: GE)(implicit b: UGenGraph.Builder): UGenInLike = in.expand

  /** Simple forwarder to `in.outputs` that can be used to access
    * the otherwise package-private method.
    */
  def outputs(in: UGenInLike): Vec[UGenInLike] = in.outputs

  /** Simple forwarder to `in.flatOutputs` that can be used to access
    * the otherwise package-private method.
    */
  def flatOutputs(in: UGenInLike): Vec[UGenIn] = in.flatOutputs

  /** Simple forwarder to `in.unbubble` that can be used to access
    * the otherwise package-private method.
    */
  def unbubble(in: UGenInLike): UGenInLike = in.unbubble

  /** Simple forwarder to `in.unwrap` that can be used to access
    * the otherwise package-private method.
    */
  def unwrapAt(in: UGenInLike, index: Int): UGenInLike = in.unwrap(index)
}

sealed trait UGenSource[U, S] extends Lazy.Expander[U] {
  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): U

  final def name: String = productPrefix

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): S
}