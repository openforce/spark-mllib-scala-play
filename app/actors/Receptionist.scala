package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.OnlineTrainer.OnlineTrainerModel
import akka.actor.{Actor, ActorRef, Props}
import org.apache.spark.SparkContext
import org.apache.spark.ml.Model
import org.apache.spark.mllib.classification.LogisticRegressionModel
import play.api.Logger
import play.api.Play.{configuration, current}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Receptionist {
  def props(sparkContext: SparkContext, eventServer: ActorRef, statisticsServer: ActorRef) = Props(new Receptionist(sparkContext, eventServer, statisticsServer))

  case object GetClassifier

  case object TrainingFinished

  val trainOnline = configuration.getBoolean("ml.trainer.online").getOrElse(false)

}

class Receptionist(sparkContext: SparkContext, eventServer: ActorRef, statisticsServer: ActorRef) extends Actor {

  import Receptionist._

  val log = Logger(this.getClass)

  val twitterHandler = context.actorOf(TwitterHandler.props(sparkContext), "twitter-handler")
  val onlineTrainer = context.actorOf(OnlineTrainer.props(sparkContext, self), "online-trainer")
  val batchTrainer = context.actorOf(BatchTrainer.props(sparkContext, self), "batch-trainer")
  val classifier = context.actorOf(Classifier.props(sparkContext, twitterHandler, onlineTrainer, batchTrainer, eventServer), "classifier")
  context.actorOf(CorpusInitializer.props(sparkContext, batchTrainer, onlineTrainer, eventServer, statisticsServer), "corpus-initializer")

  var trainersFinished: Int = 0

  override def receive = {

    case GetClassifier => sender ! classifier

    case TrainingFinished => {
      trainersFinished += 1

      // if both the batch trainer and the online trainer are done with the initial training phase
      if (trainersFinished == 2) {
//        context.system.scheduler.schedule(0 seconds, 1 seconds)({
//          batchTrainer ! GetLatestModel
//          onlineTrainer ! GetLatestModel
//        })
      }
    }

    case m: OnlineTrainerModel  => statisticsServer ! m

    case m: BatchTrainerModel => statisticsServer ! m

    case undefined => log.info(s"Unexpected message $undefined")
  }

}
