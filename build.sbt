import play.sbt.PlayScala

name := """spark-mllib-scala-play"""

version := "1.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

scalaVersion := "2.11.7"

val sparkVersion = "1.5.0"

// Needed as SBT's classloader doesn't work well with Spark
fork := true

// BUG: unfortunately, it's not supported right now
fork in console := true

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

// add a JVM option to use when forking a JVM for 'run'
javaOptions ++= Seq("-Xmx2G")

scalacOptions ++= Seq("-deprecation", "-unchecked")

resolvers ++= Seq(
  Resolver.defaultLocal,
  Resolver.mavenLocal,
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
  )

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-mllib" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  "org.jblas" % "jblas" % "1.2.4"
)

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

val sparkMode = sys.env.getOrElse("SPARK_MODE", "local[*]")

initialCommands in console :=
  s"""
    |import org.apache.spark.SparkConf
    |import org.apache.spark.SparkContext
    |import org.apache.spark.SparkContext._
    |
    |@transient val sc = new SparkContext(
    |  new SparkConf()
    |    .setMaster("$sparkMode")
    |    .setAppName("Console test"))
    |implicit def sparkContext = sc
    |import sc._
    |
    |@transient val sqlc = new org.apache.spark.sql.SQLContext(sc)
    |implicit def sqlContext = sqlc
    |import sqlc._
    |
    |def time[T](f: => T): T = {
    |  import System.{currentTimeMillis => now}
    |  val start = now
    |  try { f } finally { println("Elapsed: " + (now - start)/1000.0 + " s") }
    |}
    |
    |""".stripMargin

cleanupCommands in console :=
  s"""
     |sc.stop()
   """.stripMargin

