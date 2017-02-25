///*
// *  Demand.scala
// *  (FScape)
// *
// *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU General Public License v2+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.fscape
//package stream
//
//import akka.stream.stage.GraphStageLogic
//import akka.stream.{Attributes, FlowShape}
//import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn1DImpl, StageImpl, StageLogicImpl}
//
//object Dseq {
//  def apply(seq: OutD, repeat: OutI)(implicit b: Builder): OutD = {
//    val stage0  = new Stage
//    val stage   = b.add(stage0)
//    b.connect(in, stage.in)
//    stage.out
//  }
//
//  private final val name = "Dseq"
//
//  private type Shape = FlowShape[BufD, BufD]
//
//  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
//
//    val shape = new FlowShape(
//      in  = InD (s"$name.in" ),
//      out = OutD(s"$name.out")
//    )
//
//    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
//  }
//
//  // XXX TODO -- abstract over data type (BufD vs BufI)?
//  private final class Logic(shape: Shape)(implicit ctrl: Control)
//    extends StageLogicImpl(name, shape)
//      with GenChunkImpl[BufD, BufD, Shape]
//      with GenIn1DImpl[BufD] {
//
//    private[this] var init = true
//    private[this] var value   : Double = _
//
//    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
//      if (init) {
//        value   = bufIn0.buf(inOff)
//        init    = false
//      }
//
//      Util.fill(bufOut0.buf, outOff, chunk, value)
//    }
//  }
//}