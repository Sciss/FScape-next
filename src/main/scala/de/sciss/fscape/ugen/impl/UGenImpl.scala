/*
 *  UGenImpl.scala
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
package ugen
package impl

import scala.collection.immutable.{IndexedSeq => Vec}

final class ZeroOutImpl(val name: String, val inputs: Vec[UGenIn], val isIndividual: Boolean,
                        val specialIndex: Int)
  extends UGen.ZeroOut

final class SingleOutImpl(val name: String, val inputs: Vec[UGenIn], val isIndividual: Boolean,
                          val hasSideEffect: Boolean, val specialIndex: Int)
  extends UGen.SingleOut

final class MultiOutImpl(val name: String, val numOutputs: Int, val inputs: Vec[UGenIn],
                         val isIndividual: Boolean, val hasSideEffect: Boolean, val specialIndex: Int)
  extends UGen.MultiOut

//// side-effect: receiving messages from clients!
//// and more importantly: control ugens created from proxies are not wired, so they would
//// be eliminated if side-effect was false!!!
//object ControlImpl {
//  def apply(name: String, numChannels: Int, specialIndex: Int): ControlImpl = {
//    val res = new ControlImpl(name, numChannels = numChannels, specialIndex = specialIndex)
//    UGenGraph.builder.addUGen(res)
//    res
//  }
//}
//final class ControlImpl private(val name: String, numChannels: Int, val specialIndex: Int)
//  extends UGen.MultiOut {
//
//  def isIndividual : Boolean = false
//  def hasSideEffect: Boolean = true
//
//  def inputs: Vec[UGenIn] = Vector.empty
//}

//final class RawUGenImpl(val name: String, val numInputs: Int, val numOutputs: Int,
//                        val specialIndex: Int) extends RawUGen {
//  override def toString = {
//    //    val inputsS = inputs.map {
//    //      case Constant(f)                      => f.toString
//    //      case UGenOutProxy       (source, idx) => s"${source.name}[$idx]"
//    //      case ControlUGenOutProxy(source, idx) => s"${source.name.getOrElse("<control>")}[$idx]"
//    //      case ugen: UGen                       => ugen.name
//    //    }
//    val no = numOutputs
//    val numOutputsS = if (no           == 1) "" else s", numOutputs = $no"
//    val specialS    = if (specialIndex == 0) "" else s", specialIndex = $specialIndex"
//    // ${inputsS.mkString("[", ", ", "]")}
//    s"UGen($name, numInputs = $numInputs$numOutputsS$specialS)"
//  }
//}