package actors

import actors.Receptionist.GetClassifier
import akka.actor.{ActorRef, Props, Actor}
import org.apache.spark.SparkContext
import play.api.Logger

object Receptionist {
  def props(sparkContext: SparkContext, eventServer: ActorRef) = Props(new Receptionist(sparkContext, eventServer))

  case object GetClassifier
}

class Receptionist(sparkContext: SparkContext, eventServer: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  val twitterHandler = context.actorOf(TwitterHandler.props(sparkContext), "twitter-handler")
  val onlineTrainer = context.actorOf(OnlineTrainer.props(sparkContext), "online-trainer")
  val classifier = context.actorOf(Classifier.props(sparkContext, twitterHandler, onlineTrainer), "classifier")
  val corpusInitializer = context.actorOf(CorpusInitializer.props(sparkContext, onlineTrainer, eventServer), "corpus-initializer")

  override def receive = {

    case GetClassifier => {
      log.info(s"Get classifier")
      sender ! classifier
    }
    case undefined => log.info(s"Unexpected message $undefined")
  }

}
