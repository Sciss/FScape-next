/*
 *  package.scala
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

package de.sciss

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import scala.annotation.elidable
import scala.annotation.elidable.CONFIG

import scala.language.implicitConversions

package object fscape {
  implicit def geOps1      (g: GE    ): GEOps1 = new GEOps1(g)
  implicit def geOps2      (g: GE    ): GEOps2 = new GEOps2(g)
  implicit def intGeOps2   (i: Int   ): GEOps2 = new GEOps2(i)
  implicit def doubleGeOps2(d: Double): GEOps2 = new GEOps2(d)

  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'fscape' - ", Locale.US)

  var showGraphLog  = false
  var showStreamLog = false

//  @elidable(CONFIG) private[fscape] def logStream(what: => String): Unit =
//    if (showStreamLog) Console.out.println(s"${logHeader.format(new Date())}stream $what")

  @elidable(CONFIG) private[fscape] def logStream(what: => String): Unit =
    if (showStreamLog) {
      val w = what
      if (w.contains("completeStage" /* onUpstreamFinish" */)) Console.out.println(s"${logHeader.format(new Date())}stream $w")
    }

  @elidable(CONFIG) private[fscape] def logGraph(what: => String): Unit =
    if (showGraphLog) Console.out.println(s"${logHeader.format(new Date())}graph $what")
}
