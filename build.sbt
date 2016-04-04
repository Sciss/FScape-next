name          := "FScape"

organization  := "de.sciss"

version       := "2.0.0-SNAPSHOT"

scalaVersion  := "2.11.8"

licenses      := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

libraryDependencies ++= Seq(
  "de.sciss"            %% "scissdsp"         % "1.2.2",
  "de.sciss"            %% "numbers"          % "0.1.1",
  "de.sciss"            %% "processor"        % "0.4.0",
  "de.sciss"            %% "scalaaudiofile"   % "1.4.5",
  "de.sciss"            %% "fileutil"         % "1.1.1",
  "com.nativelibs4java" %% "scalaxy-streams"  % "0.3.4" % "provided"
)
