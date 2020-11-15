/*
 *  AbstractOutputRefPlatform.scala
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

package de.sciss.fscape.lucre.impl

import de.sciss.fscape.lucre.Cache
import de.sciss.lucre.Artifact

import scala.concurrent.stm.Ref

trait AbstractOutputRefPlatform {
  private[this] val cacheFilesRef = Ref(List.empty[Artifact.Value]) // TMap.empty[String, File] // Ref(List.empty[File])

  final def createCacheFile(): Artifact.Value = {
    val resF  = Cache.createTempFile()
    val res   = resF.toURI
    cacheFilesRef.single.transform(res :: _)
    res
  }

  final def cacheFiles: List[Artifact.Value] = cacheFilesRef.single.get
}
