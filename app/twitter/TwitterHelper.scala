package twitter

import controllers.OAuthKeys
import play.api.Logger
import play.api.Play._
import play.api.mvc.Results._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.oauth.{ConsumerKey, RequestToken, OAuthCalculator}
import play.api.libs.ws.WS
import play.api.mvc.{Action, BodyParser, RequestHeader}
import twitter4j.{Twitter, Query, TwitterFactory}
import twitter4j.conf.{Configuration, ConfigurationBuilder}
import scala.concurrent.Future
import scala.collection.JavaConversions._

case class TwitterStatus(text: String)

object TwitterStatus {
  implicit val formatter = Json.format[TwitterStatus]
}

case class TwitterStatuses(statuses: Seq[TwitterStatus])

object TwitterStatuses {
  implicit val formatter = Json.format[TwitterStatuses]
}

object TwitterHelper {

  val log = Logger(this.getClass)

  val singleUserMode = configuration.getBoolean("twitter.single-user-mode").getOrElse(true)

  val maybeConsumerKey = for {
    key <- configuration.getString("twitter.consumer.key") if(!key.isEmpty)
    secret <- configuration.getString("twitter.consumer.secret") if(!secret.isEmpty)
  } yield ConsumerKey(key, secret)

  val consumerKey = maybeConsumerKey.getOrElse(throw new IllegalStateException("""
            | ****************************************************************************************************
            | Twitter consumer key/secret pair is missing or incomplete in your configuration.
            | ****************************************************************************************************""".stripMargin))

  val config: Configuration =
      (for {
        ConsumerKey(consumerKey, consumerSecret) <- maybeConsumerKey
        accessToken <- configuration.getString("twitter.access.token") if(!accessToken.isEmpty)
        accessTokenSecret <- configuration.getString("twitter.access.secret") if(!accessTokenSecret.isEmpty)
      } yield
        new ConfigurationBuilder()
          .setDebugEnabled(true)
          .setOAuthConsumerKey(consumerKey)
          .setOAuthConsumerSecret(consumerSecret)
          .setOAuthAccessToken(accessToken)
          .setOAuthAccessTokenSecret(accessTokenSecret)
          .setUseSSL(true)
          .build()).getOrElse(throw new IllegalStateException(
        """
          |****************************************************************************************************
          | Tokens for Twitter authentication are missing in your application.conf!
          | Please get your tokens from https://dev.twitter.com/oauth/overview/application-owner-access-tokens
          | and enter them in conf/application.conf.
          |****************************************************************************************************""".stripMargin))

  val fetchUrl = configuration.getString("twitter.fetch.url").getOrElse(throw new IllegalStateException("Twitter Fetch URL is not configured."))

  def HasToken[A](b: BodyParser[A])(f: RequestToken => Action[A]): Action[A] = {
    Action.async(b) { implicit request =>
      TwitterHelper.sessionTokenPair map {
        case tokens =>
          f(tokens)(request)
      } getOrElse Future(Unauthorized(Json.obj("message" -> "Invalid or no token")))
    }
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {

    if(singleUserMode) {
      log.debug("Running in single-user-mode where oAuth per user is turned off")
      // We use the access tokens configured in the application.conf by default as request tokens because
      // this Activator template is mainly used locally by a single user and not publicly deployed
      Some(RequestToken(config.getOAuthAccessToken, config.getOAuthAccessTokenSecret))
    } else {
      for {
        token <- request.session.get("token")
        secret <- request.session.get("secret")
      } yield {
        RequestToken(token, secret)
      }
    }
  }

  def fetch(keyword: String, oAuthKeys: OAuthKeys) = {
    log.info(s"Start fetching tweets filtered by keyword=$keyword")
    val query = new Query(s"$keyword -filter:retweets").lang("en")
    val result = twitter(oAuthKeys).search(query)
    result.getTweets.take(100).map(_.getText).toList
  }

  def twitter(oAuthKeys: OAuthKeys): Twitter = {
    val config = new ConfigurationBuilder()
      .setDebugEnabled(true)
      .setOAuthConsumerKey(oAuthKeys.consumerKey.key)
      .setOAuthConsumerSecret(oAuthKeys.consumerKey.secret)
      .setOAuthAccessToken(oAuthKeys.requestToken.token)
      .setOAuthAccessTokenSecret(oAuthKeys.requestToken.secret)
      .setUseSSL(true)
      .build()
    val twitterFactory = new TwitterFactory(config)
    twitterFactory.getInstance()
  }

}
