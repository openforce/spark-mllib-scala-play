package controllers

import javax.inject._

import actors.CorpusSetupActor
import actors.CorpusSetupActor.Init
import akka.actor.ActorSystem
import org.apache.spark.{SparkConf, SparkContext}
import play.api.mvc.{Action, Controller}

@Singleton
class Application @Inject() (system: ActorSystem, sparkContext: SparkContext) extends Controller {

  val corpusSetupActor = system.actorOf(CorpusSetupActor.props(sparkContext), "corpus-setup-actor")

  def setupCorpus = Action {
    corpusSetupActor ! Init
    Ok
  }

}


