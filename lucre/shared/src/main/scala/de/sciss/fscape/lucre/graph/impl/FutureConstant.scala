/*
 *  FutureConstant.scala
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

package de.sciss.fscape.lucre.graph.impl

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.lucre.stream
import de.sciss.fscape.stream.{BufElem, Control, StreamIn, StreamOut, StreamType, Builder => SBuilder}
import de.sciss.fscape.{UGen, UGenGraph, UGenIn, UGenInLike, UGenSource}
import de.sciss.synth.UGenSource.Vec

import scala.concurrent.Future

final case class FutureConstant[A, E <: BufElem[A]](adj: Adjunct, fut: Control => Future[A])
                                                   (implicit tpe: StreamType[A, E])
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    makeUGen(Vector.empty)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
    UGen.SingleOut(this, args, adjuncts = adj :: Nil, isIndividual = true)
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): StreamOut = {
    val res = stream.FutureConstant[A, E](fut)
    tpe.mkStreamOut(res)
  }
}