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

import de.sciss.file.File

import java.net.URI

trait ControlImplPlatform {
  final def createTempFile(): File = File.createTemp()

  final def createTempURI(): URI = createTempFile().toURI
}

trait ControlPlatform {
  /** Creates a temporary file. The caller is responsible for deleting the file
    * after it is not needed any longer. (The file will still be marked `deleteOnExit`)
    */
  @deprecated("Only supported on JVM. Use platform neutral createTempURI instead", since = "3.6.0")
  def createTempFile(): File

  /** Creates a temporary file. The caller is responsible for deleting the file
    * after it is not needed any longer. (The file will still be marked `deleteOnExit`)
    */
  def createTempURI(): URI
}
