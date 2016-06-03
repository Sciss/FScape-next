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

import scala.collection.immutable.{IndexedSeq => Vec}

object UGenSource {
  trait ZeroOut extends UGenSource[Unit] {
    final protected def rewrap(args: Vec[UGenInLike], exp: Int): Unit = {
      var i = 0
      while (i < exp) {
        unwrap(args.map(_.unwrap(i)))
        i += 1
      }
    }
  }

  trait SingleOut extends SomeOut
  trait MultiOut  extends SomeOut

  protected sealed trait SomeOut extends UGenSource[UGenInLike] with GE.Lazy {
    final protected def rewrap(args: Vec[UGenInLike], exp: Int): UGenInLike =
      ugen.UGenInGroup(Vec.tabulate(exp)(i => unwrap(args.map(_.unwrap(i)))))
  }
}

sealed trait UGenSource[U] extends Lazy.Expander[U] {
  protected def makeUGen(args: Vec[UGenIn]): U

  final def name: String = productPrefix

  final protected def unwrap(args: Vec[UGenInLike]): U = {
    var uIns    = Vec.empty[UGenIn]
    var uInsOk  = true
    var exp     = 0
    args.foreach(_.unbubble match {
      case u: UGenIn => if (uInsOk) uIns :+= u
      case g: ugen.UGenInGroup =>
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

  protected def rewrap(args: Vec[UGenInLike], exp: Int): U
}