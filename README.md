# FScape-next

A laboratory project for developing the next major revision of [FScape](https://github.com/Sciss/FScape),
an experimental music and sound signal processing workbench.

FScape(-next) is (C)opyright 2001&ndash;2016 by Hanns Holger Rutz. All rights reserved.
This program is free software; you can redistribute it and/or modify it under the terms 
of the [GNU General Public License](http://github.com/Sciss/FScape-next/blob/master/LICENSE) v2+.
To contact the author, send an email to `contact at sciss.de`.

## Overview

The goals of this project are:

- completely separate GUI and processes
- move from Java to Scala
- reformulate modules as a graph of small modular DSP blocks
- DSP blocks are modelled as UGens similar to those in [ScalaCollider](https://github.com/Sciss/ScalaCollider),
  with the crucial difference that in FScape processes run in non-realtime and usually will have a bounded duration
- currently UGens graphs will be translated into an Akka Stream graph, hopefully providing a robust
  streaming API and the possibility to customise parallelism
- provide a simple DSL similar to ScalaCollider for users to write their own DSP graphs
- integrate these graphs into [Mellite](https://github.com/Sciss/Mellite)

## Notes

- early blog post: https://sciss.github.io/rethinking-fscape/