package actors


import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{Actor, Props}
import org.apache.spark.SparkContext
import play.api.Logger
import play.api.Play.{configuration, current}
import twitter.Collect
import twitter4j.conf.{Configuration, ConfigurationBuilder}

object TwitterHandler {

  def consumerKey = configuration.getString("twitter.consumer.key")
  def consumerSecret = configuration.getString("twitter.consumer.secret")
  def accessTokenKey = configuration.getString("twitter.access-token.key")
  def accessTokenSecret = configuration.getString("twitter.access-token.secret")

  private def config = {
    val cb = new ConfigurationBuilder()
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey(consumerKey.getOrElse(""))
      .setOAuthConsumerSecret(consumerSecret.getOrElse(""))
      .setOAuthAccessToken(accessTokenKey.getOrElse(""))
      .setOAuthAccessTokenSecret(accessTokenSecret.getOrElse(""))
      .build()
  }


  def props(sparkContext: SparkContext, configuration: Configuration = config) = Props(new TwitterHandler(sparkContext, configuration))

  case class Fetch(token: String)

  case class FetchResult(token: String, tweets: Seq[String])

}

class TwitterHandler(sparkContext: SparkContext, configuration: Configuration) extends Actor {

  val log = Logger(this.getClass)

  override def receive = {

    case Fetch(token) => {
      log.info(s"Start fetching tweets for token=$token")
      val tweets = Collect.fetch(token, sparkContext, configuration)
      sender ! FetchResult(token, tweets)
    }

    case undefined => log.info(s"Unexpected message $undefined")
  }

}
