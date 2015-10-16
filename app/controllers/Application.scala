package controllers

import javax.inject._
import actors.Classifier._
import actors.Receptionist
import actors.Receptionist.GetClassifier
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import play.api.libs.json.{Json, JsPath, Writes}
import play.api.mvc.{Action, Controller}
import akka.pattern._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.routing.JavaScriptReverseRouter
import twitter.LabeledTweet
import scala.concurrent.duration._

@Singleton
class Application @Inject() (system: ActorSystem, sparkContext: SparkContext) extends Controller {

  val receptionist = system.actorOf(Receptionist.props(sparkContext), "receptionist")

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

  def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.Application.classify
      )
    ).as("text/javascript")
  }

}


