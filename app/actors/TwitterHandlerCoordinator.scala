package actors

import actors.TwitterHandler.{Fetch, FetchResult}
import actors.TwitterHandlerCoordinator.FetchFor
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.apache.spark.SparkContext
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter4j.conf.Configuration

import scala.concurrent.duration._

object TwitterHandlerCoordinator {
  def props(sparkContext: SparkContext) = Props(new TwitterHandlerCoordinator(sparkContext))

  case class FetchFor(keyword: String, twitterConfig: Configuration)
}

class TwitterHandlerCoordinator(sparkContext: SparkContext) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)

  var children: Map[String, ActorRef] = Map.empty[String, ActorRef]

  override def receive = {

    case f@FetchFor(keyword: String, twitterConfig: Configuration) =>
      log.info(s"Received message $f")
      val key = twitterConfig.getOAuthConsumerKey
      val handler = children.getOrElse(key, {
          val handler = context.actorOf(TwitterHandler.props(sparkContext, twitterConfig))
          val kv = (twitterConfig.getOAuthConsumerKey -> handler)
          children = children + kv
          handler
        })

      (handler ? Fetch(keyword)).mapTo[FetchResult] map { fr =>
        sender ! fr
      }

    case unknown => log.warning(s"Unknown message received $unknown")

  }
}
