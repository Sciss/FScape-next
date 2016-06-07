name          := "FScape"
organization  := "de.sciss"
description   := "An Audio Rendering Library"
version       := "2.0.0-SNAPSHOT"
scalaVersion  := "2.11.8"
licenses      := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))
homepage      := Some(url("https://github.com/Sciss/FScape-next"))

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

lazy val akkaVersion  = "2.4.4"

libraryDependencies ++= Seq(
  "de.sciss"          %% "scissdsp"             % "1.2.2",
  "de.sciss"          %% "numbers"              % "0.1.1",
  "de.sciss"          %% "scalaaudiofile"       % "1.4.5",
  "de.sciss"          %% "fileutil"             % "1.1.1",
  "de.sciss"          %% "swingplus"            % "0.2.1",
  "de.sciss"          %% "optional"             % "1.0.0",
  "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion
)