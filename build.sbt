scalaVersion := "3.3.1"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.2",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  "ch.qos.logback" % "logback-classic" % "1.4.11"
)
