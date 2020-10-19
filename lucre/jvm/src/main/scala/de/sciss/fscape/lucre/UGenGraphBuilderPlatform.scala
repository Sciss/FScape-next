package de.sciss.fscape.lucre

import de.sciss.file.File

trait UGenGraphBuilderPlatform {
  trait OutputRefPlatform {
    /** Requests the stream control to create and memorize a
      * file that will be written during the rendering and should
      * be added as a resource associated with this key/reference.
      */
    def createCacheFile(): File
  }

  trait OutputResultPlatform {
    /** A list of cache files created during rendering for this key,
      * created via `createCacheFile()`, or `Nil` if this output did not
      * produce any additional resource files.
      */
    def cacheFiles: List[File]
  }
}
