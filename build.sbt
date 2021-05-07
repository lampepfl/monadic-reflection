lazy val commonSettings = Seq(
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.0.0-RC3"
    // javaOptions ++= Seq(
    //   "-XX:-DetectLocksInCompiledFrames",
    //   "-XX:+UnlockDiagnosticVMOptions",
    //   "-XX:+UnlockExperimentalVMOptions",
    //   "-XX:+UseNewCode")
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