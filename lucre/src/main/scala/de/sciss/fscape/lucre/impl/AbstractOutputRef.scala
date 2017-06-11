/*
 *  AbstractOutputRef.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import de.sciss.file.File
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputResult
import de.sciss.lucre.stm.Sys

import scala.concurrent.stm.Ref

/** Building block that implements all methods but `updateValue`. */
trait AbstractOutputRef[S <: Sys[S]]
  extends OutputResult[S] {

  @volatile private[this] var _writer: Output.Writer = _
  private[this] val cacheFilesRef = Ref(List.empty[File]) // TMap.empty[String, File] // Ref(List.empty[File])

  final def key: String = reader.key

  final def complete(w: Output.Writer): scala.Unit = _writer = w

  final def hasWriter: Boolean = _writer != null

  final def writer: Output.Writer = {
    if (_writer == null) {
      throw new IllegalStateException(s"Output $key was not provided")
    }
    _writer
  }

//  def updateValue(in: DataInput)(implicit tx: S#Tx): scala.Unit = {
//    val value     = reader.readOutput[S](in)
//    val output    = outputH()
//    output.value_=(Some(value))
//  }

  final def createCacheFile(): File = {
    val res = Cache.createTempFile()
    cacheFilesRef.single.transform(res :: _)
    res
  }

  final def cacheFiles: List[File] = cacheFilesRef.single.get
}