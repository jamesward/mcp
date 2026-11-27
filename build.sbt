name := "mcp-jamesward"

scalaVersion := "3.10.1"

scalacOptions ++= Seq(
  "-language:strictEquality",
  "-deprecation",
  "-Werror",
)

enablePlugins(NativeImagePlugin)

// SkillsJars: extract agent Skills with `./sbta extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))
libraryDependencies += "com.jamesward" % "skills" % "0.0.27" % Skills

// `nativeImage` builds the server as a GraalVM native binary. sbt-native-image downloads GraalVM
// Community through Coursier, so no local GraalVM is needed. The plugin's Coursier (2.0.3) can't read
// the current Coursier JVM index, and new GraalVM releases reach that index late, so the download URLs
// are pinned in project/graalvm-jvm-index.json; update both when GraalVM releases.
nativeImageJvm := "graalvm-community"
nativeImageVersion := "25.6.5.1"
nativeImageJvmIndex := (baseDirectory.value / "project" / "graalvm-jvm-index.json").toURI.toString
nativeImageOptions ++= Seq(
  // Netty initializes its classes at build time (its native-image.properties), and their static
  // SLF4J loggers are zio-logging bridge loggers, so those are built into the image too. They send
  // each log entry to the ZIO runtime that Slf4jBridge.init sets at startup.
  "--initialize-at-build-time=" + Seq(
    "zio.logging.slf4j.bridge.ZioLogger",
    "zio.logging.slf4j.bridge.LoggerData",
    "zio.logging.slf4j.bridge.LoggerData$",
    "zio.logging.slf4j.bridge.ZioLoggerFactory",
    "zio.logging.slf4j.bridge.ZioLoggerFactory$",
    "zio.logging.slf4j.bridge.ZioSLF4JServiceProvider",
    "scala.collection.convert.JavaCollectionWrappers$JConcurrentMapWrapper",
  ).mkString(","),
  // Netty calls System.loadLibrary.
  "--enable-native-access=ALL-UNNAMED",
  // The default, x86-64-v3, needs AVX2, and a dyno's CPU isn't guaranteed to have it.
  "-march=compatibility",
)

val zioVersion = "2.1.27"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"                       % zioVersion,
  "dev.zio" %% "zio-http"                  % "3.12.0",
  "dev.zio" %% "zio-logging"               % "2.5.5",
  "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.5",
  "com.jamesward" %% "zio-http-mcp"        % "0.16.1",
  "com.jamesward" %% "zio-git"             % "0.0.3",

  "dev.zio" %% "zio-test"     % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
)

fork := true

// Netty calls System.loadLibrary and scala-library's LazyVals uses sun.misc.Unsafe;
// both warn on JDK 25 until those libraries migrate.
javaOptions ++= Seq(
  "--enable-native-access=ALL-UNNAMED",
  "--sun-misc-unsafe-memory-access=allow",
)

Compile / mainClass := Some("App")
Test / mainClass := Some("AppTest")

addCommandAlias("dev", "~Test/runReload")
