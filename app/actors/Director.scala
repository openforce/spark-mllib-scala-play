package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.CorpusInitializer.Init
import actors.OnlineTrainer.OnlineTrainerModel
import akka.actor.SupervisorStrategy.{Escalate, Stop, Restart, Resume}
import akka.actor._
import akka.event.LoggingReceive
import classifiers.Predictor
import org.apache.spark.SparkContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Director {

  def props(sparkContext: SparkContext, eventServer: ActorRef, statisticsServer: ActorRef) = Props(new Director(sparkContext, eventServer, statisticsServer))

  case object GetClassifier

  case object OnlineTrainingFinished

  case object OnlineTrainerRestarted

  case object BatchTrainingFinished

}

class Director(sparkContext: SparkContext, eventServer: ActorRef, statisticsServer: ActorRef) extends Actor with ActorLogging {
  import akka.pattern.BackoffSupervisor
  import Director._

  val twitterHandler = context.actorOf(TwitterHandler.props(sparkContext), "twitter-handler")

  val onlineTrainerSupervisor = BackoffSupervisor.propsWithSupervisorStrategy(
    OnlineTrainer.props(sparkContext, self),
    childName = "online-trainer-handler",
    minBackoff = 5.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
    strategy = OneForOneStrategy()({
      case _: ActorKilledException => Restart
      case _ => Stop
    })
  )

  val onlineTrainer = context.actorOf(onlineTrainerSupervisor)

  val batchTrainer = context.actorOf(BatchTrainer.props(sparkContext, self), "batch-trainer")

  val predictor = new Predictor(sparkContext)

  val classifier = context.actorOf(Classifier.props(sparkContext, twitterHandler, onlineTrainer, batchTrainer, predictor), "classifier")

  val corpusInitializer = context.actorOf(CorpusInitializer.props(sparkContext, batchTrainer, onlineTrainer, eventServer, statisticsServer), "corpus-initializer")

  override def receive = LoggingReceive {

    case GetClassifier => sender ! classifier

    case BatchTrainingFinished => batchTrainer ! GetLatestModel

    case OnlineTrainingFinished =>
      log.debug(s"Received online trainer finished")
      context.system.scheduler.schedule(0 seconds, 5 seconds) {
        onlineTrainer ! GetLatestModel
      }

    case OnlineTrainerRestarted => {
      log.debug(s"Received online trainer restarted")
      corpusInitializer ! Init
    }

    case m: OnlineTrainerModel => statisticsServer ! m

    case m: BatchTrainerModel => statisticsServer ! m

    case undefined => log.info(s"Unexpected message $undefined")
  }

}
