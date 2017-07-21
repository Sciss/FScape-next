lazy val baseName   = "FScape"
lazy val baseNameL  = baseName.toLowerCase
lazy val githubRepo = "FScape-next"

lazy val projectVersion = "2.8.0-SNAPSHOT"
lazy val mimaVersion    = "2.7.0"

lazy val baseDescription = "An audio rendering library"

lazy val commonSettings = Seq(
  organization       := "de.sciss",
  description        := baseDescription,
  version            := projectVersion,
  scalaVersion       := "2.12.2",
  crossScalaVersions := Seq("2.12.2", "2.11.11" /* , "2.10.6" */),  // Akka does not support Scala 2.10
  licenses           := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt")),
  homepage           := Some(url(s"https://github.com/Sciss/$githubRepo")),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint"),
  resolvers          += "Oracle Repository" at "http://download.oracle.com/maven"  // required for sleepycat
) ++ publishSettings

// ---- core dependencies ----

lazy val dspVersion             = "1.2.3"
lazy val numbersVersion         = "0.1.3"
lazy val audioFileVersion       = "1.4.6"
lazy val fileUtilVersion        = "1.1.2"
lazy val swingPlusVersion       = "0.2.3"
lazy val optionalVersion        = "1.0.0"
lazy val scalaChartVersion      = "0.5.1"

lazy val akkaVersion            = "2.4.19" // N.B. "2.5.1" is latest, but they moved an impl class that we require (ActorMaterializerImpl)

// ---- lucre dependencies ----

lazy val soundProcessesVersion  = "3.13.0-SNAPSHOT"
lazy val lucreVersion           = "3.4.1"
lazy val fileCacheVersion       = "0.3.4"

// ---- test dependencies ----

lazy val scalaTestVersion       = "3.0.3"

// ---- projects ----

lazy val root = Project(id = baseNameL, base = file("."))
  .aggregate(core, lucre, cdp)
  .dependsOn(core, lucre, cdp)
  .settings(commonSettings)
  .settings(
    name := baseName,
    publishArtifact in(Compile, packageBin) := false, // there are no binaries
    publishArtifact in(Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in(Compile, packageSrc) := false, // there are no sources
    // packagedArtifacts := Map.empty
    autoScalaLibrary := false
  )

lazy val core = Project(id = s"$baseNameL-core", base = file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.fscape",
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
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion)
  )

lazy val lucre = Project(id = s"$baseNameL-lucre", base = file("lucre"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := "Bridge from FScape to SoundProcesses",
    libraryDependencies ++= Seq(
      "de.sciss"      %% "soundprocesses-core" % soundProcessesVersion,
      "de.sciss"      %% "filecache-txn"       % fileCacheVersion,
      "org.scalatest" %% "scalatest"           % scalaTestVersion % "test",
      "de.sciss"      %% "lucre-bdb"           % lucreVersion     % "test"
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-lucre" % mimaVersion)
  )

lazy val cdp = Project(id = s"$baseNameL-cdp", base = file("cdp"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := "Bridge from FScape to Composers Desktop Project",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    )
    // mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-cdp" % mimaVersion)
  )

// ---- publishing ----

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
<scm>
  <url>git@github.com:Sciss/{githubRepo}.git</url>
  <connection>scm:git:git@github.com:Sciss/{githubRepo}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
  }
)
