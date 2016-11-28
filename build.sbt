import sbt.Keys._
import sbt.Project.projectToRef

name := """guild-tools"""
version := "7.0"
crossPaths := false

lazy val scalaV = "2.11.8"
lazy val scalaOpts = Seq(
	//"-Xlog-implicits",
	"-feature",
	"-deprecation",
	"-Xfatal-warnings",
	"-unchecked",
	"-language:reflectiveCalls",
	"-language:higherKinds"
)

lazy val server = (project in file("server"))
                  .settings(
	                  scalaVersion := scalaV,
	                  scalaJSProjects := Seq(client),
	                  libraryDependencies ++= Seq(
		                  jdbc,
		                  cache,
		                  ws,
		                  "mysql" % "mysql-connector-java" % "5.1.39",
		                  "com.vmunier" %% "play-scalajs-scripts" % "0.5.0",
		                  "me.chrons" %% "boopickle" % "1.2.4",
		                  "com.typesafe.play" %% "play-slick" % "2.0.2"
	                  ),
	                  scalacOptions ++= scalaOpts,
	                  pipelineStages := Seq(scalaJSProd, digest, gzip),
	                  includeFilter in gzip := "*.html" || "*.css" || "*.js" || "*.less"
                  )
                  .enablePlugins(PlayScala, SbtWeb)
                  .aggregate(projectToRef(client))
                  .dependsOn(sharedJvm)

lazy val client = (project in file("client"))
                  .settings(
	                  scalaVersion := scalaV,
	                  scalaSource in Compile := baseDirectory.value / "src",
	                  persistLauncher := true,
	                  persistLauncher in Test := false,
	                  libraryDependencies ++= Seq(
		                  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
		                  "me.chrons" %%% "boopickle" % "1.2.4"
	                  ),
	                  scalacOptions ++= scalaOpts
                  )
                  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
                  .dependsOn(sharedJs)

lazy val shared = (crossProject.crossType(CustomCrossType) in file("shared"))
                  .settings(
	                  scalaVersion := scalaV,
	                  scalacOptions ++= scalaOpts,
	                  crossPaths := false,
	                  libraryDependencies ++= Seq(
		                  "org.scodec" %%% "scodec-core" % "1.10.1",
		                  "org.scodec" %%% "scodec-bits" % "1.1.0",
		                  "me.chrons" %%% "boopickle" % "1.2.4"
	                  )
                  )
                  .jvmSettings(
	                  scalaSource in Compile := baseDirectory.value / "src",
	                  libraryDependencies ++= Seq(
		                  "org.scala-lang" % "scala-reflect" % scalaV % "provided",
		                  "com.typesafe.play" %% "play-slick" % "2.0.2",
		                  "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided"
	                  )
                  )
                  .jsSettings(
	                  scalaSource in Compile := baseDirectory.value / "src",
	                  libraryDependencies ++= Seq(
		                  "org.scala-js" %%% "scalajs-java-time" % "0.2.0"
	                  )
                  )
                  .jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value
