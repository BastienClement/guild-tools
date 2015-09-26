name := """guild-tools"""

version := "5.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.36",
	"com.typesafe.play" %% "play-slick" % "1.0.1",
	"org.scodec" %% "scodec-core" % "1.8.2",
	"org.scodec" %% "scodec-bits" % "1.0.10"
)

pipelineStages := Seq(digest, gzip)
