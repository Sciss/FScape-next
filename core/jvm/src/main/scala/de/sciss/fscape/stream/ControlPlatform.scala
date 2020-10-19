package de.sciss.fscape.stream

import de.sciss.file.File

trait ControlImplPlatform {
  final def createTempFile(): File = File.createTemp()
}

trait ControlPlatform {
  /** Creates a temporary file. The caller is responsible for deleting the file
    * after it is not needed any longer. (The file will still be marked `deleteOnExit`)
    */
  def createTempFile(): File
}
