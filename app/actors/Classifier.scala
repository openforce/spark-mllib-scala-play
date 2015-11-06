package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.Classifier._
import actors.OnlineTrainer.{OnlineFeatures, OnlineTrainerModel}
import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import org.apache.spark.ml.{PipelineModel, Transformer}
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter.{LabeledTweet, Tweet}

import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, twitterHandler: ActorRef, onlineTrainer: ActorRef, batchTrainer: ActorRef, eventServer: ActorRef) =
    Props(new Classifier(sparkContext, twitterHandler, onlineTrainer, batchTrainer, eventServer))

  case class Classify(token: String)

  case class UpdateModel(model: PipelineModel)

  case class Point(tweet: String, tokens: Seq[String])

  case class ClassificationResult(batchModelResult: Array[LabeledTweet], onlineModelResult: Array[LabeledTweet])

}

class Classifier(sparkContext: SparkContext, twitterHandler: ActorRef, onlineTrainer: ActorRef, batchTrainer: ActorRef, eventServer: ActorRef) extends Actor with ActorLogging {

  val sqlContext = new SQLContext(sparkContext)

  implicit val timeout = Timeout(5.seconds)

  //var pipelineModel: PipelineModel = sparkContext.objectFile[PipelineModel]("app/resources/pipeline.model").first()

  override def receive =  {

    case Classify(token: String) =>
      log.info(s"Start classifying tweets for token '$token'")
      val originalSender = sender

      val handler = context.actorOf(TrainingModelResponseHandler.props(onlineTrainer, batchTrainer, originalSender, sparkContext)/*, "cameo-message-handler"*/)

      log.debug(s"Created handler $handler")
      (twitterHandler ? Fetch(token)).mapTo[FetchResult].map {
        fetchResult =>
          handler ! fetchResult
          onlineTrainer.tell(GetFeatures(fetchResult), handler)
          onlineTrainer.tell(GetLatestModel, handler)
          batchTrainer.tell(GetLatestModel, handler)
      }
  }
}

object TrainingModelResponseHandler {

  case object TrainingModelRetrievalTimeout

  def props(onlineTrainer: ActorRef, batchTrainer: ActorRef, originalSender: ActorRef, sparkContext: SparkContext) =
    Props(new TrainingModelResponseHandler(onlineTrainer, batchTrainer, originalSender, sparkContext))
}

class TrainingModelResponseHandler(onlineTrainer: ActorRef, batchTrainer: ActorRef, originalSender: ActorRef, sparkContext: SparkContext) extends Actor with ActorLogging {

  import TrainingModelResponseHandler._
  val sqlContext = new SQLContext(sparkContext)
  import sqlContext.implicits._

  var fetchResult: Option[FetchResult] = None
  var onlineFeatures: Option[RDD[(String, Vector)]] = None
  var batchTrainerModel: Option[Transformer] = None
  var onlineTrainerModel: Option[LogisticRegressionModel] = None

  def receive = LoggingReceive {

    case fr: FetchResult =>
      fetchResult = Some(fr)
      transform

    case OnlineFeatures(features) =>
      log.info(s"Received online model features: $features")
      onlineFeatures = features
      transform

    case BatchTrainerModel(model) =>
      log.info(s"Received batch trainer model: $model")
      batchTrainerModel = model
      transform

    case OnlineTrainerModel(model) =>
      onlineTrainerModel = model
      log.info(s"Received online trainer model: $model")
      transform

  }

  def transform = (fetchResult, onlineFeatures, batchTrainerModel, onlineTrainerModel) match {

    case (Some(fetchR), Some(onlineF), Some(batchM), Some(onlineM)) =>
      log.info(s"Values received for both training models")
      timeoutMessenger.cancel // TODO

      val batchModelResult =
                batchM
                  .transform(fetchR.tweets.map(t => Point(t, Tweet(t).tokens.toSeq)).toDF())
                  .select("tweet","prediction")
                  .collect()
                  .map { case Row(tweet: String, prediction: Double) =>
                    LabeledTweet(tweet, prediction.toString)
                  }

      val onlineModelResult =
              onlineF.map { case (tweet, vector) =>
                LabeledTweet(tweet, onlineM.predict(vector).toString)
              }.collect()


      sendResponseAndShutdown(ClassificationResult(batchModelResult, onlineModelResult))

    case _ =>
  }

    def sendResponseAndShutdown(response: Any) = {
      originalSender ! response
      log.info(s"Stopping context capturing actor")
      context.stop(self)
    }

    import context.dispatcher

    val timeoutMessenger = context.system.scheduler.scheduleOnce(250 millisecond) {
      self ! TrainingModelRetrievalTimeout
    }
}
