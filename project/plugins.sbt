resolvers += "Typesafe repository" at "https://dl.bintray.com/typesafe/maven-releases/"

resolvers += Resolver.sonatypeRepo("releases")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.9")

// The Play plugin

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.4")

// web plugins

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

// scala js

addSbtPlugin("com.vmunier" % "sbt-play-scalajs" % "0.3.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.10")
