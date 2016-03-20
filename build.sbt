name := """guild-tools"""

version := "6.0"

inConfig(Compile)(mappings in packageBin <++= Defaults.sourceMappings)
lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.38",
	"com.typesafe.play" %% "play-slick" % "2.0.0",
	"org.scodec" %% "scodec-core" % "1.9.0",
	"org.scodec" %% "scodec-bits" % "1.1.0"
)

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "2.2.6" % "test",
	"org.scalatestplus" %% "play" % "1.4.0" % "test"
)

scalacOptions ++= Seq("-feature")

pipelineStages := Seq(digest, gzip)
