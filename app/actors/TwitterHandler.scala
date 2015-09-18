package actors

import actors.TwitterHandler.{FetchResult, Fetch}
import akka.actor.{Props, Actor}
import org.apache.spark.SparkContext
import play.api.Logger

object TwitterHandler {

  def props(sparkContext: SparkContext) = Props(new TwitterHandler(sparkContext))

  case class Fetch(token: String)
  case class FetchResult(token: String, tweets: Seq[String])

}

class TwitterHandler(sparkContext: SparkContext) extends Actor {

  val log = Logger(this.getClass)

  override def receive = {
    case Fetch(token) =>
      sender ! FetchResult(
        token,
        Seq(
          "Awesome lectures on Deep Learning at Oxford 2015 by @NandoDF https://www.youtube.com/playlist?list=PLE6Wd9FR--EfW8dtjAuPoTuPcqmOV53Fu Great job :)",
          "#Microsoft licensing process is annoying !!!"
        )
      )
    case undefined => log.info(s"Unexpected message $undefined")
  }

}
