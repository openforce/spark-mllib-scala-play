package actors

import actors.Classifier._
import actors.TwitterHandler.{Fetch, FetchResult}
import actors.TwitterHandlerCoordinator.FetchFor
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import models.LabeledTweet
import org.apache.spark.SparkContext
import org.apache.spark.ml.PipelineModel
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, twitterHandler: ActorRef) = Props(new Classifier(sparkContext, twitterHandler))

  case class Classify(token: String)

  case class UpdateModel(model: PipelineModel)

}

class Classifier(sparkContext: SparkContext, twitterHandler: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  val sqlContext = new SQLContext(sparkContext)

  import sqlContext.implicits._

  implicit val timeout = Timeout(5.seconds)

  var pipelineModel: PipelineModel = sparkContext.objectFile[PipelineModel]("app/resources/pipeline.model").first()

  override def receive =  {

    case Classify(token: String) =>
      log.info(s"Start classifying tweets for token '$token'")
      val client = sender
      (twitterHandler ? Fetch(token)).mapTo[FetchResult] map { fr =>
        val results = pipelineModel
          .transform(sparkContext.parallelize(fr.tweets.map(t => LabeledTweet(t, ""))).toDF())
          .select("tweet", "prediction")
          .collect()
          .map { case Row(tweet: String, prediction: Double) =>
            LabeledTweet(tweet, prediction.toString)
          }
        client ! results
      }

    case UpdateModel(model: PipelineModel) =>
      log.info("Updating model")
      pipelineModel = model

  }

}
