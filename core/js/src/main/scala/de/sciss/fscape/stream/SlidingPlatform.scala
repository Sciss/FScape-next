/*
 *  SlidingPlatform.scala
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

package de.sciss.fscape.stream

import de.sciss.fscape.stream.Sliding.{Logic, Shp, Window, WindowD, WindowI, WindowL}

trait SlidingPlatform {
  protected trait LogicPlatform[A, E <: BufElem[A]]

  protected final class LogicD(shape: Shp[BufD], layer: Layer)(implicit ctrl: Control)
    extends Logic[Double, BufD](shape, layer) with LogicPlatform[Double, BufD] {

    type A = Double

    protected def mkWindow(sz: Int)(implicit ctrl: Control): Window[A] = new WindowD(new Array(sz))
  }

  protected final class LogicI(shape: Shp[BufI], layer: Layer)(implicit ctrl: Control)
    extends Logic[Int, BufI](shape, layer) with LogicPlatform[Int, BufI] {

    type A = Int

    protected def mkWindow(sz: Int)(implicit ctrl: Control): Window[A] = new WindowI(new Array(sz))
  }

  protected final class LogicL(shape: Shp[BufL], layer: Layer)(implicit ctrl: Control)
    extends Logic[Long, BufL](shape, layer) with LogicPlatform[Long, BufL] {

    type A = Long

    protected def mkWindow(sz: Int)(implicit ctrl: Control): Window[A] = new WindowL(new Array(sz))
  }
}