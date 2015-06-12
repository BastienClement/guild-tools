name := """guild-tools"""

version := "5.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.34",
	"com.typesafe.play" %% "play-slick" % "1.0.0",
	//"org.webjars" %% "webjars-play" % "2.3.0",
	"org.scodec" %% "scodec-core" % "1.7.1",
	"org.scodec" %% "scodec-bits" % "1.0.6"
)

//pipelineStages := Seq(uglify, digest, gzip)
pipelineStages := Seq(digest, gzip)

//TraceurKeys.sourceFileNames := Seq("javascripts/manifest.js", "javascripts/loader.js")
//TraceurKeys.outputFileName  := "guildtools.js"
