/*
 *  GenWindow.scala
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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{FilterIn3Impl, GenIn3Impl, WindowedFilterLogicImpl}

import scala.annotation.switch

object GenWindow {
  object Shape {
    def apply(id: Int): Shape = (id: @switch) match {
      case Hamming  .id => Hamming
      case Blackman .id => Blackman
      case Kaiser   .id => Kaiser
      case Rectangle.id => Rectangle
      case Hann     .id => Hann
      case Triangle .id => Triangle
    }
  }
  sealed trait Shape                  { def       id: Int }
  case object Hamming   extends Shape { final val id = 0  }
  case object Blackman  extends Shape { final val id = 1  }
  case object Kaiser    extends Shape { final val id = 2  }
  case object Rectangle extends Shape { final val id = 3  }
  case object Hann      extends Shape { final val id = 4  }
  case object Triangle  extends Shape { final val id = 5  }
  // XXX TODO --- we should add some standard SuperCollider curve shapes like Welch

  def apply(size: Outlet[BufI], shape: Outlet[BufI], param: Outlet[BufD])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {

    ???
  }


  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape3[BufI, BufI, BufI, BufD]] {

    val shape = new FanInShape3(
      in0 = Inlet [BufI]("GenWindow.size" ),
      in1 = Inlet [BufI]("GenWindow.shape"),
      in2 = Inlet [BufI]("GenWindow.param"),
      out = Outlet[BufD]("GenWindow.out"  )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = ??? // new Logic(shape)
  }

//  // XXX TODO -- abstract over data type (BufD vs BufI)?
//  private final class Logic(protected val shape: FanInShape3[BufI, BufI, BufI, BufD])
//                           (implicit protected val ctrl: Control)
//    extends GraphStageLogic(shape)
//      with WindowedFilterLogicImpl[BufD, BufD, FanInShape3[BufI, BufI, BufI, BufD]]
//      with GenIn3Impl                               [BufI, BufI, BufI, BufD] {
//
//    protected val in0: Inlet[BufD] = shape.in0
//
//    protected def allocOutBuf(): BufD = ctrl.borrowBufD()
//
//    private[this] var winBuf : Array[Double] = _
//    private[this] var winSize: Int = _
//    private[this] var clump  : Int = _
//
//    protected def startNextWindow(inOff: Int): Int = {
//      val oldSize = winSize
//      if (bufIn1 != null && inOff < bufIn1.size) {
//        winSize = math.max(1, bufIn1.buf(inOff))
//      }
//      if (bufIn2 != null && inOff < bufIn2.size) {
//        clump = math.max(1, bufIn2.buf(inOff))
//      }
//      if (winSize != oldSize) {
//        winBuf = new Array[Double](winSize)
//      }
//      winSize
//    }
//
//    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
//      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)
//
//    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
//      Util.copy(winBuf, readFromWinOff, bufOut.buf, outOff, chunk)
//
//    protected def processWindow(writeToWinOff: Int): Int = {
//      var i   = 0
//      val cl  = clump
//      val cl2 = cl + cl
//      var j   = writeToWinOff - cl
//      val b   = winBuf
//      while (i < j) {
//        val k = i + cl
//        while (i < k) {
//          val tmp = b(i)
//          b(i) = b(j)
//          b(j) = tmp
//          i += 1
//          j += 1
//        }
//        j -= cl2
//      }
//      writeToWinOff
//    }
//  }
}