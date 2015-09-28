package actors

import actors.CorpusInitializer.Init
import actors.Trainer.ValidateOn
import akka.actor.{Actor, ActorRef, Props}
import models.LabeledTweet
import org.apache.spark.SparkContext
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import play.api.Logger
import util.SourceUtil

object CorpusInitializer {

  def props(sparkContext: SparkContext, trainer: ActorRef) = Props(new CorpusInitializer(sparkContext, trainer))

  case object Init
}

class CorpusInitializer(sparkContext: SparkContext, trainer: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  self ! Init

  override def receive = {

    case Init => {

      log.info(s"Initialize tweets corpus")

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
              LabeledTweet(tweetText, label)
            case _ => LabeledTweet("", label)
          }
        }
      }
      .filter(_.tweet != "")

      trainer ! ValidateOn(corpus)

      context.stop(self)
    }
  }
}
