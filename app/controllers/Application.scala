package controllers

import javax.inject._

import actors.Classifier.{PredictResult, PredictResults, Predict}
import actors.Receptionist
import actors.Receptionist.GetClassifier
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import org.apache.spark.SparkContext
import play.api.libs.json.{Json, JsPath, Writes}
import play.api.mvc.{Action, Controller}
import akka.pattern._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

@Singleton
class Application @Inject() (system: ActorSystem, sparkContext: SparkContext) extends Controller {

  val receptionist = system.actorOf(Receptionist.props(sparkContext), "receptionist")

  implicit val timeout = Timeout(10.minutes)

  implicit val predictResultWrites = Json.writes[PredictResult]
  implicit val predictResultReads = Json.reads[PredictResult]

  def predict(keyword: String) = Action.async {
    for {
      classifier <- (receptionist ? GetClassifier).mapTo[ActorRef]
      predictResults <- (classifier ? Predict(keyword)).mapTo[PredictResults]
    } yield Ok(Json.toJson(predictResults.result))
  }

  def index = Action {
    Ok(views.html.index.render())
  }

}


