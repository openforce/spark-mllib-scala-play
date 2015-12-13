import play.sbt.PlayScala

name := """spark-mllib-scala-play"""

version := "1.0.4"

scalaVersion := "2.11.7"

val sparkVersion = "1.5.2"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

scalacOptions ++= Seq("-deprecation", "-unchecked")

resolvers ++= Seq(
  Resolver.defaultLocal,
  Resolver.mavenLocal,
  "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
   Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns)
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.14" % "test",
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-mllib" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-twitter" % sparkVersion,
  "org.jblas" % "jblas" % "1.2.4",
  "org.scalanlp" %% "breeze" % "0.11.2",
  "org.scalanlp" % "chalk" % "1.3.0" intransitive(),
  "org.scalanlp" % "nak" % "1.2.0" intransitive(),
  ws
)

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

mappings in Universal ++=
  (baseDirectory.value / "data" * "*" get) map
    (x => x -> ("data/" + x.getName))

fork in run := true
