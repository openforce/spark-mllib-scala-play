package controllers

import javax.inject._

import actors.Classifier._
import actors.Director.GetClassifier
import actors.FetchResponseHandler.FetchResponseTimeout
import actors.TrainingModelResponseHandler.TrainingModelRetrievalTimeout
import actors.{Director, EventListener, EventServer, StatisticsServer}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import twitter.TwitterHelper._
import scala.concurrent.duration._

@Singleton
class Application @Inject()(system: ActorSystem, sparkContext: SparkContext, twitter: Twitter) extends Controller {

  val log = Logger(this.getClass)

  val eventServer = system.actorOf(EventServer.props)

  val statisticsServer = system.actorOf(StatisticsServer.props(sparkContext))

  val director = system.actorOf(Director.props(sparkContext, eventServer, statisticsServer), "director")

  implicit val timeout = Timeout(10 seconds)

  def classify(keyword: String) = HasToken(parse.empty) { tokens =>
    Action.async(parse.empty) { implicit request =>
      (for {
        classifier <- (director ? GetClassifier).mapTo[ActorRef]
        classificationResults <- (classifier ? Classify(keyword, OAuthKeys(consumerKey, tokens))).map {
          case c: ClassificationResult => c
          case TrainingModelRetrievalTimeout => throw TimeoutException("Training models timed out.")
          case FetchResponseTimeout => throw TimeoutException("Fetching tweets timed out.")
        }
      } yield Ok(Json.toJson(classificationResults))) recover handleException
    }
  }

  def index = Action { implicit request =>
    val host = request.headers.get("X-Forwarded-Host").getOrElse(request.host)
    Ok(views.html.index.render(host))
  }

  def eventSocket = WebSocket.acceptWithActor[String, String] { request => out =>
    log.debug("Client connected to event socket")
    EventListener.props(out, eventServer)
  }

  def statisticsSocket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
    log.debug("Client connected to statistics socket")
    EventListener.props(out, statisticsServer)
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.Twitter.authenticated,
        routes.javascript.Application.classify,
        routes.javascript.Twitter.logout
      )
    ).as("text/javascript")
  }

  private def handleException: PartialFunction[Throwable, Result] = {
    case to: TimeoutException =>
      eventServer ! to.msg
      GatewayTimeout(to.msg)
    case ex => InternalServerError(ex.getMessage)
  }

}

case class TimeoutException(msg: String) extends Exception


