name := "Youth-Banking"
version := "1.0.0"
scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      // ScalaFX for desktop GUI
      "org.scalafx" %% "scalafx" % "21.0.0-R32",
      
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),
    
    // Compiler options
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation"
    ),
    
    // JavaFX dependencies (required for ScalaFX)
    fork := true
  )
