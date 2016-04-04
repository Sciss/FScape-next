package de.sciss.fscape

import de.sciss.fscape.ugen.GE

/** This is similar to `UGenIn` in ScalaCollider. */
trait Signal extends GE {
  final private[fscape] def expand: Signal = this
}
