package controllers

import javax.inject.Inject

import akka.actor.ActorSystem
import play.api.Play._
import play.api.libs.oauth._
import play.api.mvc._
import twitter.TwitterHelper._

case class OAuthKeys(consumerKey: ConsumerKey, requestToken: RequestToken)

class Twitter @Inject()(system: ActorSystem) extends Controller {

  val TWITTER = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", consumerKey),
    true)

  def authenticate = Action { request =>
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      TWITTER.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          Redirect(routes.Application.index).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
        TWITTER.retrieveRequestToken(configuration.getString("twitter.redirect.url").getOrElse("http://localhost:9000/authenticate")) match {
          case Right(t) => Redirect(TWITTER.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
          case Left(e) => throw e
        })
  }

  def logout() = Action { implicit request =>
    Redirect(routes.Application.index).withNewSession
  }

  def authenticated = Action { implicit request =>
    sessionTokenPair match {
      case Some(tokenPair) => NoContent
      case None => Unauthorized
    }
  }

}