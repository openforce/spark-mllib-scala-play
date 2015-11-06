package actors

import akka.actor.{ActorLogging, Actor, Props}
import features.TfIdf
import org.apache.spark.SparkContext
import org.apache.spark.ml.classification.{LogisticRegression, LogisticRegressionModel}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import play.api.Logger
import play.api.Play._
import twitter.{LabeledTweet, Tweet}

object BatchTrainer extends TfIdf {

  def props(sparkContext: SparkContext) = Props(new BatchTrainer(sparkContext))

  case class Point(tweet: String, label: Double, features: Vector)

  var corpus: RDD[Tweet] = _

  var model: LogisticRegressionModel = _

  val dumpCorpus = configuration.getBoolean("ml.corpus.dump").getOrElse(false)

  val dumpPath = configuration.getString("ml.corpus.path").getOrElse("")

}

class BatchTrainer(sparkContext: SparkContext) extends Actor with ActorLogging {

  import BatchTrainer._

  val sqlContext = new SQLContext(sparkContext)

  import sqlContext.implicits._

  var corpus: RDD[LabeledTweet] = sparkContext.emptyRDD[LabeledTweet]

  override def receive = {

    case Train(corpus: RDD[Tweet]) =>

      log.info(s"Start batch training")

      val data: DataFrame = corpus.map(t => Point(t.text, t.sentiment, t.toLabeledPoint.features)).toDF()

      train(corpus)

      val splits = data.randomSplit(Array(0.7, 0.3), 42)
      val trainData = splits(0)
      val testData = splits(1)

      model = new LogisticRegression()
        .fit(trainData) //Pipeline.create.fit(train)
      // sparkContext.parallelize(Seq(model), 1).saveAsObjectFile("app/resources/pipeline.model")

      var total = 0.0
      var correct = 0.0

      model
        .transform(testData)
        .select("tweet", "features", "label", "probability", "prediction")
        .collect()
        .foreach { case Row(tweet, features, label, prob, prediction) =>
          if (label == prediction) correct += 1
          total += 1
          log.info(s"'$tweet': ($label) --> prediction=$prediction ($prob)")
        }

      val precision = correct / total

      log.info(s"precision: ${precision}")

    case GetFeatures(fetchResult) =>
      log.info(s"Received GetFeatures message")
      val rdd: RDD[String] = sparkContext.parallelize(fetchResult.tweets)
      rdd.cache()
      val features = rdd map { t => (t, tfidf(Tweet(t).tokens)) }
      sender ! features

    case GetLatestModel =>
      log.info(s"Received GetLatestModel message")
      log.info(s"Return model ${model}")
      sender ! model

  }

}
