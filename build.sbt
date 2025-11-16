name := "Youth-Banking"
version := "1.0.0"
scalaVersion := "3.3.1"

// Enable Play plugin
enablePlugins(PlayScala)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      // Play Framework
      guice,
      
      // Play JSON for JsonFormats
      "com.typesafe.play" %% "play-json" % "2.10.3",
      
      // Testing
      "org.scalatestplus.play" %% "scalatestplus-play" % "6.0.0" % Test,
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),
    
    // Compiler options
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xfatal-warnings"
    )
  )
