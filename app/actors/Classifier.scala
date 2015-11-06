package actors

import actors.Classifier._
import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{ActorLogging, Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter.LabeledTweet

import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, twitterHandler: ActorRef, trainer: ActorRef) = Props(new Classifier(sparkContext, twitterHandler, trainer))

  case class Classify(token: String)

  case class UpdateModel(model: PipelineModel)

  case class Point(tweet: String, features: Vector)

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
        model <- (trainer ? GetLatestModel).mapTo[org.apache.spark.ml.classification.LogisticRegressionModel]
        foo = model
      } yield {
        val results =
          model
            .transform(features.map { case x => Point(x._1, x._2)}.toDF())
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
