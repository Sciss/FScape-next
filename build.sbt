name          := "FScape"
organization  := "de.sciss"
description   := "An Audio Rendering Library"
version       := "2.0.0-SNAPSHOT"
scalaVersion  := "2.11.8"
licenses      := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))
homepage      := Some(url("https://github.com/Sciss/FScape-next"))

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

libraryDependencies ++= Seq(
  "de.sciss"            %% "scissdsp"                 % "1.2.2",
  "de.sciss"            %% "numbers"                  % "0.1.1",
  "de.sciss"            %% "processor"                % "0.4.0",
  "de.sciss"            %% "scalaaudiofile"           % "1.4.5",
  "de.sciss"            %% "fileutil"                 % "1.1.1",
  "de.sciss"            %% "scalaaudiofile"           % "1.4.5",
  "de.sciss"            %% "swingplus"                % "0.2.1",
  // "com.typesafe.akka"   %% "akka-stream-experimental" % "2.0.4",
  "com.typesafe.akka"   %% "akka-stream"              % "2.4.4",
  "com.nativelibs4java" %% "scalaxy-streams"          % "0.3.4" % "provided"
)
