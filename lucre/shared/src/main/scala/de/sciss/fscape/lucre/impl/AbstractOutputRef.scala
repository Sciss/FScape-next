/*
 *  AbstractOutputRef.scala
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

import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputResult
import de.sciss.lucre.Txn

/** Building block that implements all methods but `updateValue`. */
trait AbstractOutputRef[T <: Txn[T]]
  extends OutputResult[T] with AbstractOutputRefPlatform {

  @volatile private[this] var _writer: Output.Writer = _

  final def key: String = reader.key

  final def complete(w: Output.Writer): scala.Unit = _writer = w

  final def hasWriter: Boolean = _writer != null

  final def writer: Output.Writer = {
    if (_writer == null) {
      throw new IllegalStateException(s"Output $key was not provided")
    }
    _writer
  }

//  def updateValue(in: DataInput)(implicit tx: T): scala.Unit = {
//    val value     = reader.readOutput[T](in)
//    val output    = outputH()
//    output.value_=(Some(value))
//  }
}