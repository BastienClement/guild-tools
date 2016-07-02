import sbt.Project.projectToRef

name := """guild-tools"""
version := "6.0"

lazy val clients = Seq(client)
lazy val scalaV = "2.11.8"

lazy val server = (project in file("server"))
                  .settings(
	                  scalaVersion := scalaV,
	                  scalaJSProjects := clients,
	                  libraryDependencies ++= Seq(
		                  jdbc,
		                  cache,
		                  ws,
		                  "mysql" % "mysql-connector-java" % "5.1.39",
		                  "com.typesafe.play" %% "play-slick" % "2.0.2",
		                  "org.scodec" %% "scodec-core" % "1.10.1",
		                  "org.scodec" %% "scodec-bits" % "1.1.0",
		                  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
		                  "org.scalatestplus" %% "play" % "1.4.0" % "test",
		                  "com.vmunier" %% "play-scalajs-scripts" % "0.5.0"
	                  ),
	                  scalacOptions ++= Seq("-feature"),
	                  pipelineStages := Seq(digest, gzip)
                  )
                  .enablePlugins(PlayScala, SbtWeb)
                  .aggregate(clients.map(projectToRef): _*)
                  .dependsOn(sharedJvm, sharedJs)

lazy val client = (project in file("client"))
                  .settings(
	                  scalaVersion := scalaV,
	                  scalaSource in Compile := baseDirectory.value / "src",
	                  persistLauncher := true,
	                  persistLauncher in Test := false,
	                  libraryDependencies ++= Seq(
		                  "org.scala-js" %%% "scalajs-dom" % "0.9.1"
	                  )
                  )
                  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
                  .dependsOn(sharedJs)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared"))
                  .settings(
	                  scalaVersion := scalaV
                  )
                  .jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value
