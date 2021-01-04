///*
// *  package.scala
// *  (FScape)
// *
// *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU Affero General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss
//
//import java.util.Date
//
//import scala.annotation.elidable
//import scala.annotation.elidable.CONFIG
//import scala.language.implicitConversions
//
//package object fscape {
//  var showGraphLog    = false
//  var showStreamLog   = false
//  var showControlLog  = false
//
//  @elidable(CONFIG) private[fscape] def logStream(what: => String): Unit =
//    if (showStreamLog) Console.out.println(s"[${new Date()}] 'fscape' - stream $what")
//
//  @elidable(CONFIG) private[fscape] def logGraph(what: => String): Unit =
//    if (showGraphLog) Console.out.println(s"[${new Date()}] 'fscape' - graph $what")
//
//  @elidable(CONFIG) private[fscape] def logControl(what: => String): Unit =
//    if (showControlLog) Console.out.println(s"[${new Date()}] 'fscape' - control $what")
//}
