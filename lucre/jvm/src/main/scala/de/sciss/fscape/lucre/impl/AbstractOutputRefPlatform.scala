package de.sciss.fscape.lucre.impl

import de.sciss.file.File
import de.sciss.fscape.lucre.Cache

import scala.concurrent.stm.Ref

trait AbstractOutputRefPlatform {
  private[this] val cacheFilesRef = Ref(List.empty[File]) // TMap.empty[String, File] // Ref(List.empty[File])

  final def createCacheFile(): File = {
    val res = Cache.createTempFile()
    cacheFilesRef.single.transform(res :: _)
    res
  }

  final def cacheFiles: List[File] = cacheFilesRef.single.get
}
