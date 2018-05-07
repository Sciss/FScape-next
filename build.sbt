lazy val baseName   = "FScape"
lazy val baseNameL  = baseName.toLowerCase
lazy val githubRepo = "FScape-next"

lazy val projectVersion = "2.14.2"
lazy val mimaVersion    = "2.14.0"

lazy val baseDescription = "An audio rendering library"

lazy val commonSettings = Seq(
  organization       := "de.sciss",
  description        := baseDescription,
  version            := projectVersion,
  scalaVersion       := "2.12.6",
  crossScalaVersions := Seq("2.12.6", "2.11.12"),
  licenses           := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt")),
  homepage           := Some(url(s"https://github.com/Sciss/$githubRepo")),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint"),
  resolvers          += "Oracle Repository" at "http://download.oracle.com/maven"  // required for sleepycat
) ++ publishSettings

lazy val deps = new {
  val main = new {
    val audioFile       = "1.5.0"
    val dsp             = "1.3.0"
    val fileUtil        = "1.1.3"
    val numbers         = "0.1.5"
    val optional        = "1.0.0"
    val scalaChart      = "0.6.0"
    val swingPlus       = "0.3.0"
    val akka            = "2.4.20" // N.B. "2.5.1" is latest, but they moved an impl class that we require (ActorMaterializerImpl)
  }
  val lucre = new {
    val fileCache       = "0.4.0"
    val soundProcesses  = "3.19.0"
  }
  val test = new {
    val kollFlitz       = "0.2.2"
    val lucre           = "3.7.0"
    val scalaTest       = "3.0.5"
    val scopt           = "3.7.0"
  }
}

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
      "de.sciss"                  %% "scissdsp"             % deps.main.dsp,
      "de.sciss"                  %% "numbers"              % deps.main.numbers,
      "de.sciss"                  %% "audiofile"            % deps.main.audioFile,
      "de.sciss"                  %% "fileutil"             % deps.main.fileUtil,
      "de.sciss"                  %% "swingplus"            % deps.main.swingPlus,
      "de.sciss"                  %% "optional"             % deps.main.optional,
      "de.sciss"                  %% "scala-chart"          % deps.main.scalaChart,
      "com.typesafe.akka"         %% "akka-stream"          % deps.main.akka,
      "com.typesafe.akka"         %% "akka-stream-testkit"  % deps.main.akka,
      "com.github.scopt"          %% "scopt"                % deps.test.scopt     % Test,
      "de.sciss"                  %% "kollflitz"            % deps.test.kollFlitz % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion)
  )

lazy val lucre = Project(id = s"$baseNameL-lucre", base = file("lucre"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := "Bridge from FScape to SoundProcesses",
    libraryDependencies ++= Seq(
      "de.sciss"      %% "soundprocesses-core" % deps.lucre.soundProcesses,
      "de.sciss"      %% "filecache-txn"       % deps.lucre.fileCache,
      "org.scalatest" %% "scalatest"           % deps.test.scalaTest % Test,
      "de.sciss"      %% "lucre-bdb"           % deps.test.lucre     % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-lucre" % mimaVersion)
  )

lazy val cdp = Project(id = s"$baseNameL-cdp", base = file("cdp"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := "Bridge from FScape to Composers Desktop Project",
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
