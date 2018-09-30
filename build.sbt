lazy val baseName   = "FScape"
lazy val baseNameL  = baseName.toLowerCase
lazy val gitRepo    = "FScape-next"

lazy val projectVersion = "2.18.0-SNAPSHOT"
lazy val mimaVersion    = "2.18.0"

lazy val baseDescription = "An audio rendering library"

lazy val commonSettings = Seq(
  organization       := "de.sciss",
  description        := baseDescription,
  version            := projectVersion,
  scalaVersion       := "2.12.7",
  crossScalaVersions := Seq("2.12.7", "2.11.12"),
  licenses           := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  homepage           := Some(url(s"https://git.iem.at/sciss/$gitRepo")),
  scalacOptions in (Compile, compile) ++= Seq(
    "-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint", "-Xsource:2.13"
  ),
  resolvers          += "Oracle Repository" at "http://download.oracle.com/maven",  // required for sleepycat
  updateOptions      := updateOptions.value.withLatestSnapshots(false)
) ++ publishSettings

lazy val deps = new {
  val main = new {
    val audioFile       = "1.5.0"
    val dsp             = "1.3.0"
    val fileUtil        = "1.1.3"
    val numbers         = "0.2.0"
    val optional        = "1.0.0"
    val scalaChart      = "0.6.0"
    val swingPlus       = "0.3.1"
    val akka            = "2.4.20" // N.B. "2.5.1" is latest, but they moved an impl class that we require (ActorMaterializerImpl)
  }
  val lucre = new {
    val fileCache       = "0.4.0"
    val soundProcesses  = "3.22.0-SNAPSHOT"
  }
  val test = new {
    val kollFlitz       = "0.2.2"
    val lucre           = "3.9.1"
    val scalaTest       = "3.0.5"
    val scopt           = "3.7.0"
  }
}

// ---- projects ----

lazy val root = project.withId(baseNameL).in(file("."))
  .aggregate(core, lucre, macros, cdp)
  .dependsOn(core, lucre, macros, cdp)
  .settings(commonSettings)
  .settings(
    name := baseName,
    publishArtifact in(Compile, packageBin) := false, // there are no binaries
    publishArtifact in(Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in(Compile, packageSrc) := false, // there are no sources
    // packagedArtifacts := Map.empty
    autoScalaLibrary := false
  )

lazy val core = project.withId(s"$baseNameL-core").in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.fscape",
    libraryDependencies ++= Seq(
      "de.sciss"          %% "scissdsp"             % deps.main.dsp,
      "de.sciss"          %% "numbers"              % deps.main.numbers,
      "de.sciss"          %% "audiofile"            % deps.main.audioFile,
      "de.sciss"          %% "fileutil"             % deps.main.fileUtil,
      "de.sciss"          %% "swingplus"            % deps.main.swingPlus,
      "de.sciss"          %% "optional"             % deps.main.optional,
      "de.sciss"          %% "scala-chart"          % deps.main.scalaChart,
      "com.typesafe.akka" %% "akka-stream"          % deps.main.akka,
      "com.typesafe.akka" %% "akka-stream-testkit"  % deps.main.akka,
      "com.github.scopt"  %% "scopt"                % deps.test.scopt     % Test,
      "de.sciss"          %% "kollflitz"            % deps.test.kollFlitz % Test,
      "org.scalatest"     %% "scalatest"            % deps.test.scalaTest % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion)
  )

lazy val lucre = project.withId(s"$baseNameL-lucre").in(file("lucre"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := s"Bridge from $baseName to SoundProcesses",
    libraryDependencies ++= Seq(
      "de.sciss"      %% "soundprocesses-core" % deps.lucre.soundProcesses,
      "de.sciss"      %% "filecache-txn"       % deps.lucre.fileCache,
      "org.scalatest" %% "scalatest"           % deps.test.scalaTest % Test,
      "de.sciss"      %% "lucre-bdb"           % deps.test.lucre     % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-lucre" % mimaVersion)
  )

lazy val macros = project.withId(s"$baseNameL-macros").in(file("macros"))
  .dependsOn(lucre)
  .settings(commonSettings)
  .settings(
    description := s"Macro support for $baseName",
    scalacOptions += "-Yrangepos",  // this is needed to extract source code
    libraryDependencies ++= Seq(
      "de.sciss" %% "soundprocesses-compiler" % deps.lucre.soundProcesses
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-macros" % mimaVersion)
  )

lazy val cdp = project.withId(s"$baseNameL-cdp").in(file("cdp"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := s"Bridge from $baseName to Composers Desktop Project",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % deps.test.scalaTest % Test
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
  <url>git@git.iem.at:sciss/{gitRepo}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{gitRepo}.git</connection>
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
