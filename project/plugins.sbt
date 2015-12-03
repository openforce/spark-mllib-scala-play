// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.4")

// Add SbtWeb
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.1.1")

// Add preprocessors
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-traceur" % "1.0.1")
