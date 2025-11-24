name := "Youth-Banking"
version := "1.0.0"
scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalafx" %% "scalafx" % "21.0.0-R32",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),
    
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation"
    ),
    
    fork := true
  )
