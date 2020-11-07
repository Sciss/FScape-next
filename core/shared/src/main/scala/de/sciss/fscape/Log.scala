/*
 *  Log.scala
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

package de.sciss.fscape

import de.sciss.log.Logger

object Log {
  val stream  : Logger = new Logger("fscape stream" )
  val graph   : Logger = new Logger("fscape graph"  )
  val control : Logger = new Logger("fscape control")
}
