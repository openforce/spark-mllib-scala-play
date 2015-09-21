package actors

import actors.Classifier._
import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import models.{CorpusItem, Pipeline}
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.collection.immutable.Queue
import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, vectorizer: ActorRef, twitterHandler: ActorRef) = Props(new Classifier(sparkContext, vectorizer, twitterHandler))

  case class Train(corpus: RDD[CorpusItem])

  case class Predict(token: String)

  case class PredictResult(tweet: String, sentiment: String)

  case class PredictResults(result: Array[PredictResult])

  case object Dequeue

}

class Classifier(sparkContext: SparkContext, vectorizer: ActorRef, twitterHandler: ActorRef) extends Actor {

  val log = Logger(this.getClass)
  val sqlContext = new SQLContext(sparkContext)
  import sqlContext.implicits._
  var pendingQueue = Queue.empty[Predict]
  implicit val timeout = Timeout(5.seconds)

  override def receive = training

  def training: Receive = {

    case Train(corpus: RDD[CorpusItem]) =>

      log.info(s"Start training")

      val data: DataFrame = corpus
        .toDF
        .filter("sentiment in ('positive', 'negative')")

      val splits = data.randomSplit(Array(0.7, 0.3), 42)
      val train = splits(0)
      val test = splits(1)

      val model = Pipeline.create.fit(train)

      var total = 0.0
      var correct = 0.0

      model.transform(test)
        .select("tweet", "sentiment", "label", "probability", "prediction")
        .collect()
        .foreach { case Row(tweet, sentiment, label, prob, prediction) =>
          if (label == prediction) correct += 1
          total += 1
          log.info(s"'$tweet': '$sentiment' ($label) --> prediction=$prediction ($prob)")
        }

      val precision = correct / total

      log.info(s"precision: ${precision}")

      context.become(predicting(model))

      self ! Dequeue

    case msg@Predict(token) => pendingQueue = pendingQueue enqueue msg

  }

  def predicting(model: PipelineModel): Receive = {

    case Dequeue =>
      while (pendingQueue.nonEmpty) {
        val (predict, newQ) = pendingQueue.dequeue
        self ! predict
        pendingQueue = newQ
      }

    case Predict(token: String) =>
      log.info(s"Start predicting")
      val client = sender
      (twitterHandler ? Fetch(token)).mapTo[FetchResult] map { fr =>
        val results = model
          .transform(sparkContext.parallelize(fr.tweets.map(t => CorpusItem("", t))).toDF())
          .select("tweet", "prediction")
          .collect()
          .map { case Row(tweet: String, prediction: Double) =>
            PredictResult(tweet, prediction.toString)
          }
        client ! PredictResults(results)
      }

  }

}
