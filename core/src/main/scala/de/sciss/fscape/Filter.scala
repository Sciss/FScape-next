/*
 *  Filter.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import scala.math.{Pi, abs, sin}

object Filter {
  def createAntiAliasFilter(impResp: Array[Double], impRespD: Array[Double],
                            halfWinSize: Int, rollOff: Double,
                            kaiserBeta: Double, samplesPerCrossing: Int): Double = {
    createLowPassFilter(impResp, 0.5 * rollOff, halfWinSize, kaiserBeta, samplesPerCrossing)

    if (impRespD != null) {
      var i = 0
      while (i < halfWinSize - 1) {
        impRespD(i) = impResp(i + 1) - impResp(i)
        i += 1
      }
      impRespD(i) = -impResp(i)
    }
    var dcGain = 0.0
    var j = samplesPerCrossing
    while (j < halfWinSize) {
      dcGain += impResp(j)
      j += samplesPerCrossing
    }
    dcGain = 2 * dcGain + impResp(0)

    1.0 / abs(dcGain)
  }

  def createLowPassFilter(impResp: Array[Double], freq: Double, halfWinSize: Int, kaiserBeta: Double,
                          samplesPerCrossing: Int): Unit = {
    val dNum		    = samplesPerCrossing.toDouble
    val smpRate		  = freq * 2.0

    // ideal lpf = infinite sinc-function; create truncated version
    impResp(0) = smpRate.toFloat
    var i = 1
    while (i < halfWinSize) {
      val d = Pi * i / dNum
      impResp(i) = (sin(smpRate * d) / d).toFloat
      i += 1
    }

    // apply Kaiser window
    import de.sciss.fscape.graph.GenWindow.Kaiser
    Kaiser.mul(winSize = halfWinSize * 2, winOff = halfWinSize, buf = impResp, bufOff = 0, len = halfWinSize,
      param = kaiserBeta)
  }
}