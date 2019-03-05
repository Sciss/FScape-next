/*
 *  Cache.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre

import de.sciss.file.File
import de.sciss.filecache.Limit

import scala.concurrent.ExecutionContext

object Cache {
  /** Queries the singleton application-wide instance. Throws exception if the cache settings
    * were not yet initialized.
    */
  def instance: Cache = {
    val res = _instance
    if (res == null) throw new IllegalStateException(s"Cache - not yet initialized")
    res
  }

  def createTempFile(): File = {
    val c       = instance
    val resExt0 = c.resourceExtension
    val resExt  = if (resExt0.startsWith(".")) resExt0 else s".$resExt0"
    java.io.File.createTempFile("fscape", resExt, c.folder)
  }

  private[this] var _instance: Cache = _

  private[this] val sync = new AnyRef

  /** Sets the singleton application-wide instance. Throws exception if the cache settings
    * were already initialized.
    */
  def init(folder: File, capacity: Limit, extension: String = "fsc", resourceExtension: String = "bin",
           executionContext: ExecutionContext = ExecutionContext.global): Unit =
    sync.synchronized {
      if (_instance != null) throw new IllegalStateException(s"Cache - already initialized")
      _instance = new Impl(folder = folder, capacity = capacity, extension = extension,
        resourceExtension = resourceExtension, executionContext = executionContext)
    }

  private final class Impl(val folder: File, val capacity: Limit, val extension: String,
                           val resourceExtension: String, val executionContext: ExecutionContext)
    extends Cache
}
trait Cache {
  def folder            : File
  def capacity          : Limit
  def extension         : String
  def resourceExtension : String
  def executionContext  : ExecutionContext
}