package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.Classifier._
import actors.FetchResponseHandler.FetchResponseTimeout
import actors.OnlineTrainer.{OnlineFeatures, OnlineTrainerModel}
import actors.TwitterHandler.{Fetch, FetchResponse}
import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import classifiers.EstimatorProxy
import org.apache.spark.SparkContext
import org.apache.spark.ml.{PipelineModel, Transformer}
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import twitter.LabeledTweet

import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, twitterHandler: ActorRef, onlineTrainer: ActorRef, batchTrainer: ActorRef, eventServer: ActorRef, estimator: EstimatorProxy) =
    Props(new Classifier(sparkContext, twitterHandler, onlineTrainer, batchTrainer, eventServer, estimator))

  case class Classify(token: String)

  case class UpdateModel(model: PipelineModel)

  case class Point(tweet: String, tokens: Seq[String])

  case class ClassificationResult(batchModelResult: Array[LabeledTweet], onlineModelResult: Array[LabeledTweet])

}

class Classifier(sparkContext: SparkContext, twitterHandler: ActorRef, onlineTrainer: ActorRef, batchTrainer: ActorRef, eventServer: ActorRef, estimator: EstimatorProxy) extends Actor with ActorLogging {

  val sqlContext = new SQLContext(sparkContext)

  implicit val timeout = Timeout(5.seconds)

  override def receive =  {

    case Classify(token: String) =>
      log.info(s"Start classifying tweets for token '$token'")
      val originalSender = sender

      val handler = context.actorOf(FetchResponseHandler.props(onlineTrainer, batchTrainer, originalSender, sparkContext, estimator), "fetch-response-message-handler")
      log.debug(s"Created handler $handler")

      twitterHandler.tell(Fetch(token), handler)
  }
}

object FetchResponseHandler {

  case object FetchResponseTimeout

  def props(onlineTrainer: ActorRef, batchTrainer: ActorRef, originalSender: ActorRef, sparkContext: SparkContext, estimator: EstimatorProxy) =
    Props(new FetchResponseHandler(onlineTrainer, batchTrainer, originalSender, sparkContext, estimator))
}

class FetchResponseHandler(onlineTrainer: ActorRef, batchTrainer: ActorRef, originalSender: ActorRef, sparkContext: SparkContext, estimator: EstimatorProxy) extends Actor with ActorLogging {


  def receive = LoggingReceive {

    case fetchResponse: FetchResponse =>
      timeoutMessenger.cancel()

      val handler = context.actorOf(TrainingModelResponseHandler.props(fetchResponse, originalSender, sparkContext, estimator), "training-model-response-message-handler")
      log.debug(s"Created handler $handler")

      onlineTrainer.tell(GetFeatures(fetchResponse), handler)
      onlineTrainer.tell(GetLatestModel, handler)
      batchTrainer.tell(GetLatestModel, handler)
      context.watch(handler)

    case t: Terminated =>
      log.debug(s"Received Terminated message for training model response handler $t")
      context.stop(self)

    case FetchResponseTimeout =>
      log.debug("Timeout occurred")
      context.stop(self)
  }

  import context.dispatcher

  val timeoutMessenger = context.system.scheduler.scheduleOnce(5 seconds) {
    self ! FetchResponseTimeout
  }
}

object TrainingModelResponseHandler {

  case object TrainingModelRetrievalTimeout

  def props(fetchResponse: FetchResponse,originalSender: ActorRef, sparkContext: SparkContext, estimator: EstimatorProxy) =
    Props(new TrainingModelResponseHandler(fetchResponse, originalSender, sparkContext, estimator))
}

class TrainingModelResponseHandler(fetchResponse: FetchResponse, originalSender: ActorRef, sparkContext: SparkContext, estimator: EstimatorProxy) extends Actor with ActorLogging {

  import TrainingModelResponseHandler._
  val sqlContext = new SQLContext(sparkContext)

  var onlineFeatures: Option[RDD[(String, Vector)]] = None
  var batchTrainerModel: Option[Transformer] = None
  var onlineTrainerModel: Option[LogisticRegressionModel] = None

  def receive = LoggingReceive {

    case OnlineFeatures(features) =>
      log.debug(s"Received online model features: $features")
      onlineFeatures = features
      predict

    case BatchTrainerModel(model) =>
      log.debug(s"Received batch trainer model: $model")
      batchTrainerModel = model
      predict

    case OnlineTrainerModel(model) =>
      onlineTrainerModel = model
      log.debug(s"Received online trainer model: $model")
      predict

    case TrainingModelRetrievalTimeout =>
      log.debug(s"Timeout occurred")
      sendResponseAndShutdown(TrainingModelRetrievalTimeout)
  }

  def predict = (onlineFeatures, batchTrainerModel, onlineTrainerModel) match {

    case (Some(onlineF), Some(batchM), Some(onlineM)) =>
      log.debug(s"Values received for online and batch training models")
      timeoutMessenger.cancel

      val batchModelResult = estimator.predict(batchM, fetchResponse)
      val onlineModelResult = estimator.predict(onlineM, onlineF)

      sendResponseAndShutdown(ClassificationResult(batchModelResult, onlineModelResult))

    case _ =>
  }

    def sendResponseAndShutdown(response: Any) = {
      originalSender ! response
      log.debug(s"Stopping context capturing actor $self")
      context.stop(self)
    }

    import context.dispatcher

    val timeoutMessenger = context.system.scheduler.scheduleOnce(5 seconds) {
      self ! TrainingModelRetrievalTimeout
    }
}
