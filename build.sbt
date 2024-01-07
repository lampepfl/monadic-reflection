
lazy val commonSettings = Seq(
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.4.0-RC1-bin-20240105-d2cc3ae-NIGHTLY",
    // javaOptions ++= Seq(
    //   "-XX:-DetectLocksInCompiledFrames",
    //   "-XX:+UnlockDiagnosticVMOptions",
    //   "-XX:+UnlockExperimentalVMOptions",
    //   "-XX:+UseNewCode")
) ++ enableContinuations

lazy val enableContinuations = Seq(
  // forking is necessary in order to enable the access of the internal vm types below.
  fork := true,

  // enable access of jdk.internal.vm.{ Continuation, ContinuationScope }
  javaOptions ++= Seq(
    "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED"
  )
)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "monadic-reflection",
    description := "Monadic Reflection for Scala"
  )

lazy val cats = project
  .in(file("cats"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("org.typelevel" % "cats-effect" % "3.1.0").cross(CrossVersion.for3Use2_13),
      ("org.typelevel" % "cats-core" % "2.3.0").cross(CrossVersion.for3Use2_13)
    )
  )

lazy val scalaz = project
  .in(file("scalaz"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("org.scalaz" %% "scalaz-core" % "7.3.3").cross(CrossVersion.for3Use2_13),
      ("org.scalaz" %% "scalaz-effect" % "7.3.3").cross(CrossVersion.for3Use2_13),
    )
  )

lazy val zio = project
  .in(file("zio"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("dev.zio" % "zio" % "1.0.7").cross(CrossVersion.for3Use2_13),
      ("dev.zio" %% "zio-streams" % "1.0.7").cross(CrossVersion.for3Use2_13)
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, cats, zio)
