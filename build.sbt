name := """guild-tools"""

version := "5.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.34",
	"com.typesafe.play" %% "play-slick" % "0.8.1",
	"org.scala-lang.modules" %% "scala-async" % "0.9.2",
	"org.webjars" %% "webjars-play" % "2.3.0",
	"org.webjars" % "angularjs" % "1.3.8",
	"org.webjars" % "jquery" % "2.1.3",
	"org.webjars" % "prefixfree" % "b44a065",
	"org.webjars" % "es6-shim" % "0.20.2",
	"org.webjars" % "showdown" % "0.3.1",
	"org.webjars" % "momentjs" % "2.8.3",
	"org.webjars" % "angular-moment" % "0.8.2"
)

//pipelineStages := Seq(uglify, digest, gzip)
pipelineStages := Seq(digest, gzip)
