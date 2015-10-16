package actors

import actors.Classifier._
import actors.OnlineTrainer.{GetFeatures, GetLatestModel}
import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter.LabeledTweet

import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, twitterHandler: ActorRef, onlineTrainer: ActorRef) = Props(new Classifier(sparkContext, twitterHandler, onlineTrainer))

  case class Classify(token: String)

  case class UpdateModel(model: PipelineModel)

}

class Classifier(sparkContext: SparkContext, twitterHandler: ActorRef, onlineTrainer: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  val sqlContext = new SQLContext(sparkContext)

  implicit val timeout = Timeout(5.seconds)

  var pipelineModel: PipelineModel = sparkContext.objectFile[PipelineModel]("app/resources/pipeline.model").first()

  override def receive =  {

    case Classify(token: String) =>
      log.info(s"Start classifying tweets for token '$token'")
      val client = sender
      for {
        fetchResult <- (twitterHandler ? Fetch(token)).mapTo[FetchResult]
        (rawData, features) <- (onlineTrainer ? GetFeatures(fetchResult)).mapTo[(RDD[String], RDD[Vector])]
        model <- (onlineTrainer ? GetLatestModel).mapTo[LogisticRegressionModel]
      } yield {
        val results = model.predict(features).zip(rawData).map { case (sentiment, tweet) =>
          LabeledTweet(tweet, sentiment.toString)
        }.collect()
        client ! results
      }
  }

}
