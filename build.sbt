// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
//import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val baseName   = "FScape"
lazy val baseNameL  = baseName.toLowerCase
lazy val gitRepo    = "FScape-next"

lazy val projectVersion = "2.29.0-SNAPSHOT"
lazy val mimaVersion    = "2.29.0"

lazy val baseDescription = "An audio rendering library"

lazy val commonSettings = Seq(
  organization       := "de.sciss",
  description        := baseDescription,
  version            := projectVersion,
  scalaVersion       := "2.12.9",
  crossScalaVersions := Seq("2.13.0", "2.12.9"),
  licenses           := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  homepage           := Some(url(s"https://git.iem.at/sciss/$gitRepo")),
  scalacOptions in (Compile, compile) ++= Seq(
    "-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint", "-Xsource:2.13"
  ),
  scalacOptions in (Compile, compile) ++= (if (scala.util.Properties.isJavaAtLeast("9")) Seq("-release", "8") else Nil), // JDK >8 breaks API; skip scala-doc
  resolvers          += "Oracle Repository" at "http://download.oracle.com/maven",  // required for sleepycat
  updateOptions      := updateOptions.value.withLatestSnapshots(false),
  javacOptions        := commonJavaOptions ++ Seq("-target", "1.8", "-g", "-Xlint:deprecation" /*, "-Xlint:unchecked" */),
  javacOptions in doc := commonJavaOptions,
  parallelExecution in Test := false
) ++ publishSettings

lazy val deps = new {
  val main = new {
    val audioFile       = "1.5.3"
    val dsp             = "1.3.2"
    val fileUtil        = "1.1.3"
    val numbers         = "0.2.0"
    val optional        = "1.0.0"
    val scalaChart      = "0.7.1"
    val swingPlus       = "0.4.2"
    val akka            = "2.5.24"
  }
  val lucre = new {
    val fileCache       = "0.5.1"
    val lucre           = "3.14.0-SNAPSHOT"
    val soundProcesses  = "3.31.0-SNAPSHOT"
  }
  val views = new {
    val lucreSwing      = "1.18.0-SNAPSHOT"
  }
  val modules = new {
    val scallop         = "3.3.1"
  }
  val test = new {
    val kollFlitz       = "0.2.3"
    val scalaTest       = "3.0.8"
    val scallop: String = modules.scallop
    val submin          = "0.2.5"
  }
}

def commonJavaOptions = Seq("-source", "1.8")

lazy val testSettings = Seq(
  libraryDependencies += {
    "org.scalatest" %% "scalatest" % deps.test.scalaTest % Test
  }
)

// ---- projects ----

lazy val root = project.withId(baseNameL).in(file("."))
  // .aggregate(core.jvm, core.js, lucre.jvm, macros.jvm, cdp.jvm, modules.jvm, views.jvm)
  .aggregate(core, lucre, macros, cdp, modules, views)
//  .dependsOn(core, lucre, macros, cdp, modules, views)
  .settings(commonSettings)
  .settings(
    name := baseName,
    publishArtifact in(Compile, packageBin) := false, // there are no binaries
    publishArtifact in(Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in(Compile, packageSrc) := false, // there are no sources
    // packagedArtifacts := Map.empty
    autoScalaLibrary := false
  )

lazy val core = project
  // crossProject(JSPlatform, JVMPlatform)
  // .withoutSuffixFor(JVMPlatform)
  // .crossType(CrossType.Pure)
  .withId(s"$baseNameL-core")
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-Core",
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.fscape",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %%  "akka-stream"         % deps.main.akka,
      "com.typesafe.akka" %%  "akka-stream-testkit" % deps.main.akka,
      "de.sciss"          %%  "audiofile"           % deps.main.audioFile,
      "de.sciss"          %%  "fileutil"            % deps.main.fileUtil,
      "de.sciss"          %%  "numbers"             % deps.main.numbers,
      "de.sciss"          %%  "optional"            % deps.main.optional,
      "de.sciss"          %%  "scissdsp"            % deps.main.dsp,
      "de.sciss"          %%  "swingplus"           % deps.main.swingPlus,
      "de.sciss"          %%  "scala-chart"         % deps.main.scalaChart,
      "de.sciss"          %%  "kollflitz"           % deps.test.kollFlitz % Test
    ),
    libraryDependencies += {
      // if (scalaVersion.value == "2.13.0-RC2") {
      //   "com.github.scopt" % "scopt_2.13.0-RC1" % deps.test.scopt % Test
      // } else {
      //   "com.github.scopt" %% "scopt" % deps.test.scopt % Test
      // }
      "org.rogach" %% "scallop" % deps.test.scallop % Test
    },
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion),
    // scalaJSUseMainModuleInitializer in Test := true,
    mainClass in Test := Some("de.sciss.fscape.FramesTest")
  )
  // .jsSettings(
  //   libraryDependencies ++= Seq(
  //     "org.akka-js" %%% "akkajsactorstream" % "1.2.5.21"
  //   )
  // )

lazy val lucre = project
  // crossProject(JVMPlatform)
  // .withoutSuffixFor(JVMPlatform)
  .withId(s"$baseNameL-lucre")
  .in(file("lucre"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-Lucre",
    description := s"Bridge from $baseName to SoundProcesses",
    libraryDependencies ++= Seq(
      "de.sciss"        %% "lucre-core"          % deps.lucre.lucre,
      "de.sciss"        %% "lucre-expr"          % deps.lucre.lucre,
      "de.sciss"        %% "soundprocesses-core" % deps.lucre.soundProcesses,
      "de.sciss"        %% "filecache-txn"       % deps.lucre.fileCache,
      "org.scala-lang"  %  "scala-reflect"       % scalaVersion.value,
      "de.sciss"        %% "lucre-bdb"           % deps.lucre.lucre % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-lucre" % mimaVersion)
  )

lazy val macros = project
  // crossProject(JVMPlatform)
  // .withoutSuffixFor(JVMPlatform)
  .withId(s"$baseNameL-macros")
  .in(file("macros"))
  .dependsOn(lucre)
  .settings(commonSettings)
  .settings(
    name := s"$baseName-Macros",
    description := s"Macro support for $baseName",
    scalacOptions += "-Yrangepos",  // this is needed to extract source code
    libraryDependencies ++= Seq(
      "de.sciss" %% "soundprocesses-compiler" % deps.lucre.soundProcesses
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-macros" % mimaVersion)
  )

lazy val views = project
  // crossProject(JVMPlatform)
  // .withoutSuffixFor(JVMPlatform)
  .withId(s"$baseNameL-views")
  .in(file("views"))
  .dependsOn(lucre)
  .settings(commonSettings)
  .settings(
    name := s"$baseName-Views",
    description := s"Widget elements for $baseName",
    libraryDependencies ++= Seq(
      "de.sciss"  %% "lucreswing" % deps.views.lucreSwing,
      "de.sciss"  %% "lucre-bdb"  % deps.lucre.lucre    % Test,
      "de.sciss"  %  "submin"     % deps.test.submin    % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-views" % mimaVersion)
  )

lazy val modules = project
  // crossProject(JVMPlatform)
  // .withoutSuffixFor(JVMPlatform)
  .withId(s"$baseNameL-modules")
  .in(file("modules"))
  .dependsOn(macros, views)
  .settings(commonSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-Modules",
    description := s"Bringing $baseName v1 modules to the next generation",
    scalacOptions += "-Yrangepos",  // this is needed to extract source code
    libraryDependencies ++= Seq(
      "de.sciss"          %% "lucre-core"           % deps.lucre.lucre,
      "de.sciss"          %% "lucre-expr"           % deps.lucre.lucre,
      "de.sciss"          %% "lucre-bdb"            % deps.lucre.lucre,
      "de.sciss"          %% "lucreswing"           % deps.views.lucreSwing,
      "de.sciss"          %% "soundprocesses-views" % deps.lucre.soundProcesses,
      // "com.github.scopt"  %% "scopt"                % deps.modules.scopt,
      "org.rogach"        %% "scallop"              % deps.modules.scallop
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-modules" % mimaVersion)
  )

lazy val cdp = project
  // crossProject(JVMPlatform)
  // .withoutSuffixFor(JVMPlatform)
  .withId(s"$baseNameL-cdp")
  .in(file("cdp"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-CDP",
    description := s"Bridge from $baseName to Composers Desktop Project",
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-cdp" % mimaVersion)
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
