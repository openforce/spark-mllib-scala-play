package actors

import akka.actor.{Actor, ActorRef, Props}
import org.apache.spark.SparkContext
import play.api.Logger
import play.api.Play.{configuration, current}

object Receptionist {
  def props(sparkContext: SparkContext, eventServer: ActorRef) = Props(new Receptionist(sparkContext, eventServer))

  case object GetClassifier

  val trainOnline = configuration.getBoolean("ml.trainer.online").getOrElse(false)

}

class Receptionist(sparkContext: SparkContext, eventServer: ActorRef) extends Actor {

  import Receptionist._

  val log = Logger(this.getClass)

  val twitterHandler = context.actorOf(TwitterHandler.props(sparkContext), "twitter-handler")
  val onlineTrainer = context.actorOf(OnlineTrainer.props(sparkContext), "online-trainer")
  val batchTrainer = context.actorOf(BatchTrainer.props(sparkContext), "batch-trainer")
  val classifier = context.actorOf(Classifier.props(sparkContext, twitterHandler, onlineTrainer, batchTrainer, eventServer), "classifier")
  context.actorOf(CorpusInitializer.props(sparkContext, batchTrainer, onlineTrainer, eventServer), "corpus-initializer")

  override def receive = {

    case GetClassifier => {
      log.debug(s"Received GetClassifier message")
      sender ! classifier
    }
    case undefined => log.warn(s"Unexpected message $undefined")
  }

}
