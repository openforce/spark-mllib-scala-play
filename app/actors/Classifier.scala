package actors

import actors.Classifier._
import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter.{LabeledTweet, Tweet}

import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, twitterHandler: ActorRef, trainer: ActorRef) = Props(new Classifier(sparkContext, twitterHandler, trainer))

  case class Classify(token: String)

  case class UpdateModel(model: PipelineModel)

  case class Point(tweet: String, tokens: Seq[String])

}

class Classifier(sparkContext: SparkContext, twitterHandler: ActorRef, trainer: ActorRef) extends Actor with ActorLogging {

  val sqlContext = new SQLContext(sparkContext)

  implicit val timeout = Timeout(5.seconds)

  import sqlContext.implicits._

  //var pipelineModel: PipelineModel = sparkContext.objectFile[PipelineModel]("app/resources/pipeline.model").first()

  override def receive =  {

    case Classify(token: String) =>
      log.info(s"Start classifying tweets for token '$token'")
      val client = sender
      for {
        fetchResult <- (twitterHandler ? Fetch(token)).mapTo[FetchResult]
        features <- (trainer ? GetFeatures(fetchResult)).mapTo[RDD[(String, Vector)]]
//        model <- (trainer ? GetLatestModel).mapTo[org.apache.spark.mllib.classification.LogisticRegressionModel]
        model <- (trainer ? GetLatestModel).mapTo[org.apache.spark.ml.Model[_]]
      } yield {
        val results =
          model
            .transform(fetchResult.tweets.map(t => Point(t, Tweet(t).tokens.toSeq)).toDF())
            .select("tweet","prediction")
            .collect()
            .map { case Row(tweet: String, prediction: Double) =>
              LabeledTweet(tweet, prediction.toString)
            }
// online trainer
//        features.map { case (tweet, vector) =>
//          LabeledTweet(tweet, model.predict(vector).toString)
//        }.collect()

        client ! results
      }

  }

}
