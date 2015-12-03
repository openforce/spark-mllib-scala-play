package twitter

import controllers.OAuthKeys
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS

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

  val numTweetsToCollect = 10

  val log = Logger(this.getClass)

  def fetch(keyword: String, oAuthKeys: OAuthKeys): Future[Seq[String]] = {
    log.info(s"Start fetching tweets filtered by keyword=$keyword")

    val tweets = WS.url("https://api.twitter.com/1.1/search/tweets.json?q=spectre&lang=en")
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
