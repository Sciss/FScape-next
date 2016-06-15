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
  implicit def geOps(g: GE): GEOps = new GEOps(g)

  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'fscape' - ", Locale.US)

  var showGraphLog  = false
  var showStreamLog = false

  @elidable(CONFIG) private[fscape] def logStream(what: => String): Unit =
    if (showStreamLog) Console.out.println(s"${logHeader.format(new Date())}stream $what")

  @elidable(CONFIG) private[fscape] def logGraph(what: => String): Unit =
    if (showGraphLog) Console.out.println(s"${logHeader.format(new Date())}graph $what")
}
