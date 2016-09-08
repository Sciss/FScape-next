/*
 *  UGenSource.scala
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

import de.sciss.fscape.graph.UGenInGroup
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object UGenSource {
  trait ZeroOut extends UGenSource[Unit, Unit] {
    final protected def rewrap(args: Vec[UGenInLike], exp: Int)(implicit b: UGenGraph.Builder): Unit = {
      var i = 0
      while (i < exp) {
        unwrap(args.map(_.unwrap(i)))
        i += 1
      }
    }
  }

  trait SingleOut extends SomeOut[StreamOut]
  trait MultiOut  extends SomeOut[Vec[StreamOut]]

  protected sealed trait SomeOut[S] extends UGenSource[UGenInLike, S] with GE.Lazy {
    final protected def rewrap(args: Vec[UGenInLike], exp: Int)(implicit b: UGenGraph.Builder): UGenInLike =
      UGenInGroup(Vec.tabulate(exp)(i => unwrap(args.map(_.unwrap(i)))))
  }
}

sealed trait UGenSource[U, S] extends Lazy.Expander[U] {
  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): U

  final def name: String = productPrefix

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): S

  final protected def unwrap(args: Vec[UGenInLike])(implicit b: UGenGraph.Builder): U = {
    var uIns    = Vec.empty[UGenIn]
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
      makeUGen(uIns)
    } else {
      rewrap(args, exp)
    }
  }

  protected def rewrap(args: Vec[UGenInLike], exp: Int)(implicit b: UGenGraph.Builder): U
}