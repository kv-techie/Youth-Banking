name := "Youth-Banking"
version := "1.0.0"
scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      guice, // Play Dependency Injection
      "com.typesafe.play" %% "play-json" % "2.10.3", // Play JSON
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "6.0.0" % Test
    )
    // NO scalacOptions here—Play sets these for you!
  )
