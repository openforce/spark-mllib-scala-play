package twitter

import controllers.OAuthKeys
import play.api.Logger
import play.api.Play.{configuration, current}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS
import twitter4j.conf.{Configuration, ConfigurationBuilder}

import scala.concurrent.Future

case class _Status(text: String)

object _Status {
  implicit val formatter = Json.format[_Status]
}

case class Statuses(statuses: Seq[_Status])

object Statuses {

  implicit val formatter = Json.format[Statuses]

}

object TwitterHelper {

  def config: Configuration =
    (for {
      consumerKey <- configuration.getString("twitter.consumer.key")
      consumerSecret <- configuration.getString("twitter.consumer.secret")
      accessTokenKey <- configuration.getString("twitter.access-token.key")
      accessTokenSecret <- configuration.getString("twitter.access-token.secret")
    } yield
      new ConfigurationBuilder()
        .setDebugEnabled(true)
        .setOAuthConsumerKey(consumerKey)
        .setOAuthConsumerSecret(consumerSecret)
        .setOAuthAccessToken(accessTokenKey)
        .setOAuthAccessTokenSecret(accessTokenSecret)
        .setUseSSL(true)
        .build()).getOrElse(throw new IllegalStateException(
      """
        |****************************************************************************************************
        | Tokens for Twitter authentication are missing in your application.conf!
        | Please get your tokens from https://dev.twitter.com/oauth/overview/application-owner-access-tokens
        | and enter them in conf/application.conf.
        |****************************************************************************************************""".stripMargin))


  val numTweetsToCollect = 10

  // TODO document in README.md
  val twitterFetchUrl = configuration.getString("twitter.fetch.url").get

  val log = Logger(this.getClass)

  def fetch(keyword: String, oAuthKeys: OAuthKeys): Future[Seq[String]] = {
    log.info(s"Start fetching tweets filtered by keyword=$keyword")

    val tweets = WS.url(s"${twitterFetchUrl}${keyword}")
      .sign(OAuthCalculator(oAuthKeys.consumerKey, oAuthKeys.requestToken))
      .get
      .map(result => {
        result.json.as[Statuses]
      })
      .map(statuses => {
        statuses.statuses.map(status => status.text)
      })

    tweets
  }

}
