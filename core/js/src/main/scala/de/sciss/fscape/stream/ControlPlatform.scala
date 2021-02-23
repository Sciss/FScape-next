/*
 *  ControlPlatform.scala
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

package de.sciss.fscape.stream

import de.sciss.asyncfile.IndexedDBFileSystemProvider

import java.net.URI

object ControlImplPlatform {
  private[this] var tmpCnt = 0

  final def createTempURI(): URI = {
    val cnt   = synchronized {
      tmpCnt += 1
      tmpCnt
    }
    val cntS  = (cnt + 0x100000000L).toString.substring(1)
    val path  = s"temp$cntS"
    new URI(IndexedDBFileSystemProvider.scheme, null, path, null)
  }
}
trait ControlImplPlatform {
  final def createTempURI(): URI = ControlImplPlatform.createTempURI()
}

trait ControlPlatform {
  /** Creates a temporary file. The caller is responsible for deleting the file
    * after it is not needed any longer. (The file will still be marked `deleteOnExit`)
    */
  def createTempURI(): URI
}