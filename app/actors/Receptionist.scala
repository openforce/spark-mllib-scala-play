package actors

import actors.Receptionist.GetClassifier
import akka.actor.{ActorRef, Props, Actor}
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
  val trainer = context.actorOf(if (trainOnline) OnlineTrainer.props(sparkContext) else BatchTrainer.props(sparkContext), "trainer")
  val classifier = context.actorOf(Classifier.props(sparkContext, twitterHandler, trainer), "classifier")
  context.actorOf(CorpusInitializer.props(sparkContext, trainer, eventServer), "corpus-initializer")

  override def receive = {

    case GetClassifier => {
      log.info(s"Get classifier")
      sender ! classifier
    }
    case undefined => log.info(s"Unexpected message $undefined")
  }

}
