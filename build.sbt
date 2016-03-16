name := """guild-tools"""

version := "6.0"

inConfig(Compile)(mappings in packageBin <++= Defaults.sourceMappings)
lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.38",
	"com.typesafe.play" %% "play-slick" % "1.1.1",
	"org.scodec" %% "scodec-core" % "1.8.3",
	"org.scodec" %% "scodec-bits" % "1.0.12"
)

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "2.2.5" % "test",
	"org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)

scalacOptions ++= Seq("-feature")

routesGenerator := InjectedRoutesGenerator

pipelineStages := Seq(digest, gzip)
