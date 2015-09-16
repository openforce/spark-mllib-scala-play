package actors

import actors.BatchTrainingActor.Test
import actors.CorpusSetupActor.Init
import akka.actor.{Actor, Props}
import models.CorpusItem
import org.apache.spark.SparkContext
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import play.api.Logger
import util.SourceUtil

import scala.io.{BufferedSource, Source}

object CorpusSetupActor {

  def props(sparkContext: SparkContext) = Props(new CorpusSetupActor(sparkContext))

  case object Init
}

class CorpusSetupActor(sparkContext: SparkContext) extends Actor {

  val log = Logger(this.getClass)

  val batchTrainer = context.actorOf(BatchTrainingActor.props(sparkContext), "batch-trainer")

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

      batchTrainer ! Test(corpus)
    }
  }
}
