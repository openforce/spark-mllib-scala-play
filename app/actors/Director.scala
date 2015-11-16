package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.OnlineTrainer.OnlineTrainerModel
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import classifiers.Estimator
import org.apache.spark.SparkContext
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Director {

  def props(sparkContext: SparkContext, eventServer: ActorRef, statisticsServer: ActorRef) = Props(new Director(sparkContext, eventServer, statisticsServer))

  case object GetClassifier

  case object OnlineTrainingFinished

  case object BatchTrainingFinished

}

class Director(sparkContext: SparkContext, eventServer: ActorRef, statisticsServer: ActorRef) extends Actor {

  import Director._

  val log = Logger(this.getClass)

  val twitterHandler = context.actorOf(TwitterHandler.props(sparkContext), "twitter-handler")

  val onlineTrainer = context.actorOf(OnlineTrainer.props(sparkContext, self), "online-trainer")

  val batchTrainer = context.actorOf(BatchTrainer.props(sparkContext, self), "batch-trainer")

  val estimator = new Estimator(sparkContext)

  val classifier = context.actorOf(Classifier.props(sparkContext, twitterHandler, onlineTrainer, batchTrainer, eventServer, estimator), "classifier")

  context.actorOf(CorpusInitializer.props(sparkContext, batchTrainer, onlineTrainer, eventServer, statisticsServer), "corpus-initializer")

  override def receive = LoggingReceive {

    case GetClassifier => sender ! classifier

    case BatchTrainingFinished =>
      batchTrainer ! GetLatestModel

    case OnlineTrainingFinished =>
      context.system.scheduler.schedule(0 seconds, 5 seconds) {
        onlineTrainer ! GetLatestModel
      }

    case m: OnlineTrainerModel => statisticsServer ! m

    case m: BatchTrainerModel => statisticsServer ! m

    case undefined => log.info(s"Unexpected message $undefined")
  }

}
