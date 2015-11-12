package controllers

import javax.inject._

import actors.Classifier._
import actors.Director.GetClassifier
import actors.{EventListener, EventServer, Director, StatisticsServer}
import akka.actor.{Props, ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, Controller, WebSocket}
import play.api.routing.JavaScriptReverseRouter
import twitter.LabeledTweet

import scala.concurrent.duration._

@Singleton
class Application @Inject() (system: ActorSystem, sparkContext: SparkContext) extends Controller {

  val log = Logger(this.getClass)
  val eventServer = system.actorOf(EventServer.props)
  val statisticsServer = system.actorOf(StatisticsServer.props(sparkContext))
  val receptionist = system.actorOf(Director.props(sparkContext, eventServer, statisticsServer), "receptionist")

  implicit val timeout = Timeout(5 seconds)
  implicit val formats = Json.format[LabeledTweet]

  def classify(keyword: String) = Action.async {
    for {
      classifier <- (receptionist ? GetClassifier).mapTo[ActorRef]
      classificationResults <- (classifier ? Classify(keyword)).mapTo[ClassificationResult]
    } yield Ok(Json.toJson(classificationResults.onlineModelResult))
  }

  def index = Action {
    Ok(views.html.index.render)
  }

  def socket = WebSocket.acceptWithActor[String, String] { request => out =>
    log.debug(s"Client connected to socket")
    EventListener.props(out, eventServer)
  }

  def statisticsSocket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
    EventListener.props(out, statisticsServer)
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.Application.classify
      )
    ).as("text/javascript")
  }

}


