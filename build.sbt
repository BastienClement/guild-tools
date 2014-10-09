name := """guild-tools"""

version := "5.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws
)

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.27"

libraryDependencies += "com.typesafe.play" %% "play-slick" % "0.8.0"

pipelineStages := Seq(uglify, digest, gzip)
