package actors

import actors.TwitterHandler.{Fetch, FetchResponse}
import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import org.apache.spark.SparkContext
import play.api.Logger
import play.api.Play.{configuration, current}
import twitter.TwitterHelper
import twitter4j.conf.{Configuration, ConfigurationBuilder}

object TwitterHandler {

  def consumerKey = configuration.getString("twitter.consumer.key")
  def consumerSecret = configuration.getString("twitter.consumer.secret")
  def accessTokenKey = configuration.getString("twitter.access-token.key")
  def accessTokenSecret = configuration.getString("twitter.access-token.secret")

  def config: Configuration =
    new ConfigurationBuilder()
      .setDebugEnabled(true)
      .setOAuthConsumerKey(consumerKey.getOrElse(""))
      .setOAuthConsumerSecret(consumerSecret.getOrElse(""))
      .setOAuthAccessToken(accessTokenKey.getOrElse(""))
      .setOAuthAccessTokenSecret(accessTokenSecret.getOrElse(""))
      .setUseSSL(true)
      .build()

  def props(sparkContext: SparkContext, configuration: Configuration = config) = Props(new TwitterHandler(sparkContext, configuration))

  case class Fetch(keyword: String)

  case class FetchResponse(keyword: String, tweets: Seq[String])

}

trait TwitterHandlerProxy extends Actor

class TwitterHandler(sparkContext: SparkContext, configuration: Configuration) extends Actor with TwitterHandlerProxy {

  val log = Logger(this.getClass)

  override def receive = LoggingReceive {

    case Fetch(keyword) =>
      log.debug(s"Received Fetch message with keyword=$keyword from $sender")
      val tweets = TwitterHelper.fetch(keyword, sparkContext, configuration)
      sender ! FetchResponse(keyword, tweets)

    case undefined => log.warn(s"Unexpected message $undefined")
  }

}
