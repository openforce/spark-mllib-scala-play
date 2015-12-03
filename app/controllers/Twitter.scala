package controllers

import javax.inject.Inject

import akka.actor.ActorSystem
import play.api.Play._
import play.api.libs.oauth._
import play.api.mvc._

case class OAuthKeys(consumerKey: ConsumerKey, requestToken: RequestToken)

class Twitter @Inject()(system: ActorSystem) extends Controller {

  val KEY = ConsumerKey(
    configuration.getString("twitter.consumer.key").getOrElse(""),
    configuration.getString("twitter.consumer.secret").getOrElse("")
  )

  val TWITTER = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", KEY),
    true)

  def authenticate = Action { request =>
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      TWITTER.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          // Notify the director about the new oauth credentials
          system.actorSelection("/user/director") ! OAuthKeys(KEY, tokenPair)

          Redirect(routes.Application.index).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
        TWITTER.retrieveRequestToken(configuration.getString("twitter.redirect.url").getOrElse("http://127.0.0.1:9000/authenticate")) match {
          case Right(t) => Redirect(TWITTER.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
          case Left(e) => throw e
        })
  }

  def logout() = Action { implicit request =>
    Redirect(routes.Application.index).withNewSession
  }

  def authenticated = Action { implicit request =>
    sessionTokenPair match {
      case Some(tokenPair) => {
        // Notify the director about the new oauth credentials
        system.actorSelection("/user/director") ! OAuthKeys(KEY, tokenPair)

        NoContent
      }
      case None => Unauthorized
    }
  }

  def keys = Action { implicit request =>
    KEY.key.equals("") && KEY.secret.equals("") match {
      case true => InternalServerError
      case false => NoContent
    }
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }
}