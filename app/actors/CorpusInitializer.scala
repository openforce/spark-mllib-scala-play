package actors

import actors.Classifier.Train
import actors.CorpusInitializer.Init
import akka.actor.{ActorRef, Actor, Props}
import models.CorpusItem
import org.apache.spark.SparkContext
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import play.api.Logger
import util.SourceUtil

import scala.io.{BufferedSource, Source}

object CorpusInitializer {

  def props(sparkContext: SparkContext, classifier: ActorRef) = Props(new CorpusInitializer(sparkContext, classifier))

  case object Init
}

class CorpusInitializer(sparkContext: SparkContext, classifier: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  self ! Init

  override def receive = {

    case Init => {

      log.info(s"Init corpus")

      val corpus = sparkContext.textFile("data/corpus.csv")
        .map { _.split(",") }
        .map { r =>
        implicit val formats = DefaultFormats
        val trimmed = r.map(_.replaceAll("\"",""))
        val (label, id) = (trimmed(1), trimmed(2))
        SourceUtil.readSourceOpt(s"data/rawdata/$id.json") { maybeBufferedSource =>
          maybeBufferedSource match {
            case Some(bufferedSource) =>
              val tweet = parse(bufferedSource.mkString)
              val tweetText = if ((tweet \ "user" \ "lang").extract[String] == "en") (tweet \ "text").extract[String] else ""
              CorpusItem(label, tweetText)
            case _ => CorpusItem(label, "")
          }
        }
      }
      .filter(_.tweet != "")

      classifier ! Train(corpus)

//      context.stop(self)
    }
  }
}
