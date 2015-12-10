package actors

import actors.TwitterHandler.{Fetch, FetchResponse}
import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import controllers.OAuthKeys
import org.apache.spark.SparkContext
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter.TwitterHelper

object TwitterHandler {

  def props(sparkContext: SparkContext) = Props(new TwitterHandler(sparkContext))

  case class Fetch(keyword: String, keys: OAuthKeys)

  case class FetchResponse(keyword: String, tweets: Seq[String])

}

trait TwitterHandlerProxy extends Actor

class TwitterHandler(sparkContext: SparkContext) extends Actor with TwitterHandlerProxy {

  val log = Logger(this.getClass)

  var oAuthKeys: OAuthKeys = _

  override def receive = LoggingReceive {

    case Fetch(keyword, oAuthKeys) =>
      log.debug(s"Received Fetch message with keyword=$keyword from $sender")
      val tweets = TwitterHelper.fetch(keyword, oAuthKeys)
      sender ! FetchResponse(keyword, tweets)

    case undefined => log.warn(s"Unexpected message $undefined")

  }

}
