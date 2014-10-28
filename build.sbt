name := """guild-tools"""

version := "5.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.27",
	"com.typesafe.play" %% "play-slick" % "0.8.0",
	"com.typesafe.play" %% "play-slick" % "0.8.0",
	"org.scala-lang.modules" %% "scala-async" % "0.9.2",
	"org.webjars" %% "webjars-play" % "2.3.0-2",
	"org.webjars" % "angularjs" % "1.3.0-rc.4",
	"org.webjars" % "modernizr" % "2.7.1",
	"org.webjars" % "jquery" % "2.1.1",
	"org.webjars" % "prefixfree" % "b44a065",
	"org.webjars" % "es6-shim" % "0.12.0",
	"org.webjars" % "showdown" % "0.3.1",
	"org.webjars" % "momentjs" % "2.8.3",
	"org.webjars" % "angular-moment" % "0.8.2"
)

//pipelineStages := Seq(uglify, digest, gzip)
pipelineStages := Seq(gzip)
