package actors

import actors.Classifier.UpdateModel
import actors.Trainer.{TrainBatch, TrainOnline, ValidateOn}
import akka.actor.{Actor, ActorRef, Props}
import classifiers.Pipeline
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.streaming.dstream.DStream
import play.api.Logger
import twitter.LabeledTweet

object Trainer {

  def props(sparkContext: SparkContext, classifier: ActorRef, twitterHandler: ActorRef) = Props(new Trainer(sparkContext, classifier, twitterHandler))

  case class ValidateOn(data: RDD[LabeledTweet])

  case class TrainBatch(batch: RDD[LabeledTweet])

  case class TrainOnline(stream: DStream[LabeledTweet])

}

class Trainer(sparkContext: SparkContext, classifier: ActorRef, twitterHandler: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  val sqlContext = new SQLContext(sparkContext)

  import sqlContext.implicits._

  var corpus: RDD[LabeledTweet] = sparkContext.emptyRDD[LabeledTweet]

  override def receive = {

    case ValidateOn(data: RDD[LabeledTweet]) =>

      corpus = data

    case TrainBatch(batch: RDD[LabeledTweet]) =>

      log.info(s"Start batch training")

      val data: DataFrame = batch
        .toDF
        .filter("sentiment in ('positive', 'negative')")

      val splits = data.randomSplit(Array(0.7, 0.3), 42)
      val train = splits(0)
      val test = splits(1)

      val model = Pipeline.create.fit(train)
      // sparkContext.parallelize(Seq(model), 1).saveAsObjectFile("app/resources/pipeline.model")

      var total = 0.0
      var correct = 0.0

      model
        .transform(test)
        .select("tweet", "sentiment", "label", "probability", "prediction")
        .collect()
        .foreach { case Row(tweet, sentiment, label, prob, prediction) =>
          if (label == prediction) correct += 1
          total += 1
          log.info(s"'$tweet': '$sentiment' ($label) --> prediction=$prediction ($prob)")
        }

      val precision = correct / total

      log.info(s"precision: ${precision}")

      classifier ! UpdateModel(model)

  }

}
