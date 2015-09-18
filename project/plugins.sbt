// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.2")

// Add SbtWeb and Traceur
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.1.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-traceur" % "1.0.0")
