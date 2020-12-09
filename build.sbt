lazy val baseName   = "FScape"
lazy val baseNameL  = baseName.toLowerCase
lazy val gitRepo    = "FScape-next"

lazy val projectVersion = "3.4.1-SNAPSHOT"
lazy val mimaVersion    = "3.4.0"

lazy val baseDescription = "An audio rendering library"

lazy val commonJvmSettings = Seq(
  crossScalaVersions := Seq(/* "3.0.0-M2", */ "2.13.4", "2.12.12"),
)

// sonatype plugin requires that these are in global
ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val commonSettings = Seq(
//  version            := projectVersion,
//  organization       := "de.sciss",
  description        := baseDescription,
  scalaVersion       := "2.13.4",
  licenses           := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  homepage           := Some(url(s"https://git.iem.at/sciss/$gitRepo")),
  scalacOptions ++= Seq(
    "-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint", "-Xsource:2.13"
  ),
  scalacOptions in (Compile, compile) ++= {
    val dot = isDotty.value
    val xs  = (if (!dot && scala.util.Properties.isJavaAtLeast("9")) Seq("-release", "8") else Nil)  // JDK >8 breaks API; skip scala-doc
    val sv  = scalaVersion.value
    if (sv.startsWith("2.13.")) xs :+ "-Wvalue-discard" else xs
  },
  updateOptions      := updateOptions.value.withLatestSnapshots(false),
  javacOptions        := commonJavaOptions ++ Seq("-target", "1.8", "-g", "-Xlint:deprecation" /*, "-Xlint:unchecked" */),
  javacOptions in doc := commonJavaOptions,
  parallelExecution in Test := false,
  concurrentRestrictions in Global ++= Seq(
    Tags.limitAll(2), Tags.limit(Tags.Test, 1) // with cross-builds we otherwise get OutOfMemoryError
  ),
) ++ publishSettings

lazy val deps = new {
  val core = new {
    val akka            = "2.6.10"  // on the JVM
    val akkaJs          = "2.2.6.9" // on JS
    val audioFile       = "2.3.2"
    val dom             = "1.1.0"
    val dsp             = "2.2.1"
    val fileUtil        = "1.1.5"
    val linKernighan    = "0.1.3"
    val log             = "0.1.1"
    val numbers         = "0.2.1"
    val optional        = "1.0.1"
    val scalaChart      = "0.8.0"
    val swingPlus       = "0.5.0"
    val transform4s     = "0.1.1"
  }
  val lucre = new {
    val fileCache       = "1.1.1"
    val lucre           = "4.3.0"
    val soundProcesses  = "4.5.0"
  }
  val views = new {
    val lucreSwing      = "2.5.0"
  }
  val modules = new {
    val scallop         = "3.5.1"
  }
  val test = new {
    val kollFlitz       = "0.2.4"
    val scalaTest       = "3.2.3"
    val scallop: String = modules.scallop
    val submin          = "0.3.4"
  }
}

def commonJavaOptions = Seq("-source", "1.8")

lazy val testSettings = Seq(
  libraryDependencies += {
    "org.scalatest" %%% "scalatest" % deps.test.scalaTest % Test
  },
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
)

// ---- projects ----

lazy val root = project.withId(baseNameL).in(file("."))
  .aggregate(
    core .jvm, core .js,
    lucre.jvm, lucre.js,
    macros, cdp, modules, views,
  )
//  .dependsOn(core, lucre, macros, cdp, modules, views)
  .settings(commonSettings)
//  .settings(commonJvmSettings)
  .settings(
    name := baseName,
    publish := {},
    publishArtifact := false,
    autoScalaLibrary := false,
    mimaFailOnNoPrevious := false
  )

lazy val core = crossProject(JVMPlatform, JSPlatform).in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .jvmSettings(commonJvmSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-Core",
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.fscape",
    libraryDependencies ++= Seq(
      "de.sciss"          %%%  "audiofile"            % deps.core.audioFile,
      "de.sciss"          %%%  "scissdsp"             % deps.core.dsp,
      "de.sciss"          %%%  "transform4s"          % deps.core.transform4s,
      "de.sciss"          %%%  "linkernighantsp"      % deps.core.linKernighan,
      "de.sciss"          %%%  "log"                  % deps.core.log,
      "de.sciss"          %%%  "numbers"              % deps.core.numbers,
      "de.sciss"          %%%  "optional"             % deps.core.optional,
      "de.sciss"          %%%  "kollflitz"            % deps.test.kollFlitz  % Test,
      "org.rogach"        %%%  "scallop"              % deps.test.scallop    % Test,
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion),
    mainClass in Test := Some("de.sciss.fscape.FramesTest")
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %%%  "akka-stream"          % deps.core.akka,
      "com.typesafe.akka" %%%  "akka-stream-testkit"  % deps.core.akka,
      "de.sciss"          %%%  "fileutil"             % deps.core.fileUtil,
      "de.sciss"          %%%  "scala-chart"          % deps.core.scalaChart,
      "de.sciss"          %%%  "swingplus"            % deps.core.swingPlus,
    ),
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.akka-js"       %%% "akkajsactorstream"     % deps.core.akkaJs,
      "org.akka-js"       %%% "akkajsstreamtestkit"   % deps.core.akkaJs,
      "org.scala-js"      %%% "scalajs-dom"           % deps.core.dom,
    )
  )

lazy val lucre = crossProject(JVMPlatform, JSPlatform).in(file("lucre"))
  .dependsOn(core)
  .settings(commonSettings)
  .jvmSettings(commonJvmSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-Lucre",
    description := s"Bridge from $baseName to SoundProcesses",
    libraryDependencies ++= Seq(
      "de.sciss"        %%% "lucre-core"          % deps.lucre.lucre,
      "de.sciss"        %%% "lucre-expr"          % deps.lucre.lucre,
      "de.sciss"        %%% "soundprocesses-core" % deps.lucre.soundProcesses,
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-lucre" % mimaVersion)
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "de.sciss"        %%% "filecache-txn"       % deps.lucre.fileCache,
      "org.scala-lang"  %   "scala-reflect"       % scalaVersion.value,
      "de.sciss"        %%% "lucre-bdb"           % deps.lucre.lucre % Test,
    )
  )

lazy val macros = project
  .withId(s"$baseNameL-macros")
  .in(file("macros"))
  .dependsOn(lucre.jvm)
  .settings(commonSettings)
  .settings(commonJvmSettings)
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
  .withId(s"$baseNameL-views")
  .in(file("views"))
  .dependsOn(lucre.jvm)
  .settings(commonSettings)
  .settings(commonJvmSettings)
  .settings(
    name := s"$baseName-Views",
    description := s"Widget elements for $baseName",
    libraryDependencies ++= Seq(
      "de.sciss"  %% "lucre-swing"  % deps.views.lucreSwing,
      "de.sciss"  %% "lucre-bdb"    % deps.lucre.lucre    % Test,
      "de.sciss"  %  "submin"       % deps.test.submin    % Test
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-views" % mimaVersion)
  )

lazy val modules = project
  .withId(s"$baseNameL-modules")
  .in(file("modules"))
  .dependsOn(macros, views)
  .settings(commonSettings)
  .settings(commonJvmSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-Modules",
    description := s"Bringing $baseName v1 modules to the next generation",
    scalacOptions += "-Yrangepos",  // this is needed to extract source code
    libraryDependencies ++= Seq(
      "de.sciss"          %% "lucre-core"           % deps.lucre.lucre,
      "de.sciss"          %% "lucre-expr"           % deps.lucre.lucre,
      "de.sciss"          %% "lucre-bdb"            % deps.lucre.lucre,
      "de.sciss"          %% "lucre-swing"          % deps.views.lucreSwing,
      "de.sciss"          %% "soundprocesses-views" % deps.lucre.soundProcesses,
      "org.rogach"        %% "scallop"              % deps.modules.scallop
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-modules" % mimaVersion)
  )

lazy val cdp = project
  .withId(s"$baseNameL-cdp")
  .in(file("cdp"))
  .dependsOn(core.jvm)
  .settings(commonSettings)
  .settings(commonJvmSettings)
  .settings(testSettings)
  .settings(
    name := s"$baseName-CDP",
    description := s"Bridge from $baseName to Composers Desktop Project",
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-cdp" % mimaVersion)
  )

// ---- publishing ----

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "git.iem.at"
    val a = s"sciss/$gitRepo"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)

