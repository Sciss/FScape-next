///*
// *  BinaryInImpl.scala
// *  (FScape)
// *
// *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU Affero General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.fscape
//package stream
//package impl
//
//import akka.stream.FanInShape2
//import akka.stream.stage.GraphStageLogic
//
///** Building block for `FanInShape2` type graph stage logic,
//  * where both left and right inlet are treated equal, and
//  * the process only terminates when both inlets have terminated.
//  */
//trait BinaryInImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, Out >: Null <: BufLike]
//  extends In2Impl[In0, In1, Out] {
//  _: GraphStageLogic with Node =>
//
//  // ---- impl ----
//
//  private[this] final var _canRead = false
//  private[this] final var _inValid = false
//
//  final def canRead: Boolean = _canRead
//  final def inValid: Boolean = _inValid
//
//  protected final def readIns(): Int = {
//    freeInputBuffers()
//
//    var sz = 0
//    if (isAvailable(in0)) {
//      bufIn0 = grab(in0)
//      tryPull(in0)
//      sz = bufIn0.size
//    }
//
//    if (isAvailable(in1)) {
//      bufIn1 = grab(in1)
//      tryPull(in1)
//      if (bufIn1.size > sz) sz = bufIn1.size
//    }
//
//    _inValid = true
//    _canRead = false
////    updateCanRead()
////    if (_canRead) println("Hepa!")
////    require(sz > 0)
//    sz
//  }
//
//  final def updateCanRead(): Unit = {
//    val a0 = isAvailable(in0)
//    val a1 = isAvailable(in1)
//    // if inputs are valid, either of the two inlets must be available
//    // and both must be available or closed.
//    // if inputs are not yet valid, both inlets must be available.
//    _canRead = if (_inValid) {
//      (a0 || a1) && ((a0 || (isClosed(in0) && !isAvailable(in0))) &&
//                     (a1 || (isClosed(in1) && !isAvailable(in1))))
//    } else {
//      a0 && a1
//    }
//
////    println(s"a0 = $a0, a1 = $a1, canRead = ${_canRead}")
//  }
//
//  new EquivalentInHandlerImpl(in0 , this)
//  new EquivalentInHandlerImpl(in1 , this)
//  new ProcessOutHandlerImpl  (out0, this)
//}
//
//trait BinaryInDImpl[In0 >: Null <: BufLike, In1 >: Null <: BufLike]
//  extends BinaryInImpl[In0, In1, BufD] with Out1DoubleImpl[FanInShape2[In0, In1, BufD]] {
//
//  _: GraphStageLogic with Node =>
//}