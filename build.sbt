name               := "FScape"
organization       := "de.sciss"
description        := "An Audio Rendering Library"
version            := "2.0.0-SNAPSHOT"
scalaVersion       := "2.11.8"
crossScalaVersions := Seq("2.11.8", "2.10.6")
licenses           := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))
homepage           := Some(url("https://github.com/Sciss/FScape-next"))

scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

lazy val dspVersion         = "1.2.2"
lazy val numbersVersion     = "0.1.1"
lazy val audioFileVersion   = "1.4.5"
lazy val fileUtilVersion    = "1.1.1"
lazy val swingPlusVersion   = "0.2.1"
lazy val optionalVersion    = "1.0.0"
lazy val scalaChartVersion  = "0.5.0"
lazy val akkaVersion        = "2.4.9"

libraryDependencies ++= Seq(
  "de.sciss"                  %% "scissdsp"             % dspVersion,
  "de.sciss"                  %% "numbers"              % numbersVersion,
  "de.sciss"                  %% "scalaaudiofile"       % audioFileVersion,
  "de.sciss"                  %% "fileutil"             % fileUtilVersion,
  "de.sciss"                  %% "swingplus"            % swingPlusVersion,
  "de.sciss"                  %% "optional"             % optionalVersion,
  "com.github.wookietreiber"  %% "scala-chart"          % scalaChartVersion,
  "com.typesafe.akka"         %% "akka-stream"          % akkaVersion,
  "com.typesafe.akka"         %% "akka-stream-testkit"  % akkaVersion
)
