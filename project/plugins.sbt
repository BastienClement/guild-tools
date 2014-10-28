resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.6")

// The Play plugin

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.6")

// web plugins

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "1.0.3")
