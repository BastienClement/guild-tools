name := """guild-tools"""

version := "5.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.34",
	"com.typesafe.play" %% "play-slick" % "0.8.1",
	"org.webjars" %% "webjars-play" % "2.3.0",
	"org.webjars" % "angularjs" % "1.3.15",
	"org.webjars" % "jquery" % "2.1.4",
	"org.webjars" % "prefixfree" % "b44a065",
	"org.webjars" % "es6-shim" % "0.20.2",
	"org.webjars" % "showdown" % "0.3.1",
	"org.webjars" % "momentjs" % "2.10.2",
	"org.webjars" % "angular-moment" % "0.9.0",
	"org.scodec" %% "scodec-core" % "1.7.1",
	"org.scodec" %% "scodec-bits" % "1.0.6"
)

//pipelineStages := Seq(uglify, digest, gzip)
pipelineStages := Seq(digest, gzip)

//TraceurKeys.sourceFileNames := Seq("javascripts/manifest.js", "javascripts/loader.js")
//TraceurKeys.outputFileName  := "guildtools.js"
