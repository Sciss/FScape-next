/*
 *  AbstractOutputRefPlatform.scala
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

package de.sciss.fscape.lucre.impl

import de.sciss.lucre.Artifact

import scala.concurrent.stm.Ref

trait AbstractOutputRefPlatform {
  private[this] val cacheFilesRef = Ref(List.empty[Artifact.Value])

  final def createCacheFile(): Artifact.Value = ???

  final def cacheFiles: List[Artifact.Value] = cacheFilesRef.single.get

}
