package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.Classifier._
import actors.FetchResponseHandler.FetchResponseTimeout
import actors.OnlineTrainer.{OnlineFeatures, OnlineTrainerModel}
import actors.TwitterHandler.{Fetch, FetchResponse}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.util.Timeout
import org.apache.spark.SparkContext
import org.apache.spark.ml.{PipelineModel, Transformer}
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
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

      val handler = context.actorOf(FetchResponseHandler.props(onlineTrainer, batchTrainer, originalSender, sparkContext)/*, "cameo-message-handler"*/)

      log.debug(s"Created handler $handler")

      twitterHandler.tell(Fetch(token), handler)
  }
}


object FetchResponseHandler {

  case object FetchResponseTimeout

  def props(onlineTrainer: ActorRef, batchTrainer: ActorRef, originalSender: ActorRef, sparkContext: SparkContext) =
    Props(new FetchResponseHandler(onlineTrainer, batchTrainer, originalSender, sparkContext))
}

class FetchResponseHandler(onlineTrainer: ActorRef, batchTrainer: ActorRef, originalSender: ActorRef, sparkContext: SparkContext) extends Actor with ActorLogging {


  def receive = LoggingReceive {

    case fetchResponse: FetchResponse =>
      timeoutMessenger.cancel()

      val handler = context.actorOf(TrainingModelResponseHandler.props(fetchResponse, originalSender, sparkContext)/*, "cameo-message-handler"*/)
      onlineTrainer.tell(GetFeatures(fetchResponse), handler)
      onlineTrainer.tell(GetLatestModel, handler)
      batchTrainer.tell(GetLatestModel, handler)

    case FetchResponseTimeout =>
      log.debug("Timeout occurred")
      sendResponseAndShutdown(FetchResponseTimeout)
  }

  def sendResponseAndShutdown(response: Any) = {
    originalSender ! response
    log.debug(s"Stopping context capturing actor")
    context.stop(self)
  }

  import context.dispatcher

  val timeoutMessenger = context.system.scheduler.scheduleOnce(5 seconds) {
    self ! FetchResponseTimeout
  }
}

object TrainingModelResponseHandler {

  case object TrainingModelRetrievalTimeout

  def props(fetchResponse: FetchResponse,originalSender: ActorRef, sparkContext: SparkContext) =
    Props(new TrainingModelResponseHandler(fetchResponse, originalSender, sparkContext))
}

class TrainingModelResponseHandler(fetchResponse: FetchResponse, originalSender: ActorRef, sparkContext: SparkContext) extends Actor with ActorLogging {

  import TrainingModelResponseHandler._
  val sqlContext = new SQLContext(sparkContext)
  import sqlContext.implicits._

  var onlineFeatures: Option[RDD[(String, Vector)]] = None
  var batchTrainerModel: Option[Transformer] = None
  var onlineTrainerModel: Option[LogisticRegressionModel] = None

  def receive = LoggingReceive {

    case OnlineFeatures(features) =>
      log.debug(s"Received online model features: $features")
      onlineFeatures = features
      transform

    case BatchTrainerModel(model) =>
      log.debug(s"Received batch trainer model: $model")
      batchTrainerModel = model
      transform

    case OnlineTrainerModel(model) =>
      onlineTrainerModel = model
      log.debug(s"Received online trainer model: $model")
      transform

    case TrainingModelRetrievalTimeout =>
      log.debug(s"Timeout occurred")
      sendResponseAndShutdown(TrainingModelRetrievalTimeout)
  }

  def transform = (onlineFeatures, batchTrainerModel, onlineTrainerModel) match {

    case (Some(onlineF), Some(batchM), Some(onlineM)) =>
      log.debug(s"Values received for online and batch training models")
      timeoutMessenger.cancel

      val batchModelResult =
                batchM
                  .transform(fetchResponse.tweets.map(t => Point(t, Tweet(t).tokens.toSeq)).toDF())
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
      log.debug(s"Stopping context capturing actor")
      context.stop(self)
    }

    import context.dispatcher

    val timeoutMessenger = context.system.scheduler.scheduleOnce(5 seconds) {
      self ! TrainingModelRetrievalTimeout
    }
}
