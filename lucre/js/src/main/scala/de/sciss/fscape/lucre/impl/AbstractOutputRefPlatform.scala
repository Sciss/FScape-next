package de.sciss.fscape.lucre.impl

import de.sciss.lucre.Artifact

import scala.concurrent.stm.Ref

trait AbstractOutputRefPlatform {
  private[this] val cacheFilesRef = Ref(List.empty[Artifact.Value])

  final def createCacheFile(): Artifact.Value = ???

  final def cacheFiles: List[Artifact.Value] = cacheFilesRef.single.get

}
