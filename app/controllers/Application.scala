package controllers

import javax.inject._

import actors.Classifier._
import actors.EventServer.Subscribe
import actors.Receptionist.GetClassifier
import actors.{EventServer, Receptionist}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import org.apache.spark.SparkContext
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, WebSocket}
import play.api.routing.JavaScriptReverseRouter
import twitter.LabeledTweet

import scala.concurrent.duration._

@Singleton
class Application @Inject() (system: ActorSystem, sparkContext: SparkContext) extends Controller {

  val eventServer = system.actorOf(EventServer.props)
  val receptionist = system.actorOf(Receptionist.props(sparkContext, eventServer), "receptionist")

  implicit val timeout = Timeout(10.minutes)
  implicit val formats = Json.format[LabeledTweet]

  def classify(keyword: String) = Action.async {
    for {
      classifier <- (receptionist ? GetClassifier).mapTo[ActorRef]
      classificationResults <- (classifier ? Classify(keyword)).mapTo[Array[LabeledTweet]]
    } yield Ok(Json.toJson(classificationResults))
  }

  def index = Action {
    Ok(views.html.index.render)
  }

  def socket = WebSocket.acceptWithActor[String, String] { request => out =>
    EventListener.props(out)
  }

  object EventListener {

    def props(out: ActorRef) = Props(new EventListener(out))

    case object GetFeedback
  }

  class EventListener(out: ActorRef) extends Actor {

    override def preStart() = {
      eventServer ! Subscribe
    }

    def receive = {
      case msg: String =>
        out ! msg
    }
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.Application.classify
      )
    ).as("text/javascript")
  }

}


