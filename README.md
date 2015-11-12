# Typesafe Activator tutorial for Apache Spark, MLlib, Scala, Akka and Play Framework

[![TravisCI](https://travis-ci.org/openforce/spark-mllib-scala-play.svg?branch=develop)](https://travis-ci.org/openforce/spark-mllib-scala-play/)

With this tutorial template we show how to automatically classify the sentiment of Twitter messages leveraging the Typesafe Stack and Apache Spark. These messages are classified as either positive or negative with respect to a query term. Users who want to research the sentiment of products before purchase, or companies that want to monitor the public sentiment of their brands can make use of this kind of application. The Activator template will consist of a backend component using Scala, Spark, Akka and the Play Framework in their most recent versions. The core part will demonstrate the usage of machine learning algorithms for classifying the sentiment of Twitter messages using Apache Spark and MLlib. The approach of sentiment classification used in this template is based on the paper by Alec Go et al. [(2009)](http://cs.stanford.edu/people/alecmgo/papers/TwitterDistantSupervision09.pdf ) and its related implementation [(Alec Go et al., 2010)](http://www.sentiment140.com/).

## Setup Instructions

Assuming that you have [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [Sbt](http://www.scala-sbt.org/) already installed on your machine please do:

1. Clone this repository: `git clone git@github.com:openforce/spark-mllib-scala-play.git`
1. Change into the newly created directory: `cd spark-mllib-scala-play`
1. Launch SBT: `sbt run`
1. Navigate your browser to: <http://localhost:9000>

## The Classification Workflow

The following diagram shows how the actor communication workflow looks like:

![The Classification Workflow](tutorial/images/actors.jpg)