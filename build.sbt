import sbt.Project.projectToRef

name := """guild-tools"""
version := "7.0"

lazy val clients = Seq(client)
lazy val scalaV = "2.11.8"
lazy val scalaOpts = Seq(
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
	                  scalaJSProjects := clients,
	                  libraryDependencies ++= Seq(
		                  jdbc,
		                  cache,
		                  ws,
		                  "mysql" % "mysql-connector-java" % "5.1.39",
		                  "com.typesafe.play" %% "play-slick" % "2.0.0",
		                  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
		                  "org.scalatestplus" %% "play" % "1.4.0" % "test",
		                  "com.vmunier" %% "play-scalajs-scripts" % "0.5.0",
		                  "me.chrons" %% "boopickle" % "1.2.4"
	                  ),
	                  scalacOptions ++= scalaOpts,
	                  pipelineStages := Seq(scalaJSProd, digest, gzip)
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
		                  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
		                  "me.chrons" %%% "boopickle" % "1.2.4"
	                  ),
	                  scalacOptions ++= scalaOpts
                  )
                  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
                  .dependsOn(sharedJs)

lazy val shared = (crossProject.crossType(CrossType.Full) in file("shared"))
                  .settings(
	                  scalaVersion := scalaV,
	                  scalacOptions ++= scalaOpts
                  )
                  .jvmSettings(
	                  libraryDependencies ++= Seq(
		                  "org.scodec" %% "scodec-core" % "1.10.1",
		                  "org.scodec" %% "scodec-bits" % "1.1.0",
		                  "me.chrons" %% "boopickle" % "1.2.4",
		                  "com.typesafe.play" %% "play-slick" % "2.0.0",
		                  "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided"
	                  )
                  )
                  .jsSettings(
	                  libraryDependencies ++= Seq(
		                  "org.scodec" %%% "scodec-core" % "1.10.1",
		                  "org.scodec" %%% "scodec-bits" % "1.1.0",
		                  "me.chrons" %%% "boopickle" % "1.2.4",
		                  "org.scala-js" %%% "scalajs-java-time" % "0.2.0"
	                  )
                  )
                  .jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value
