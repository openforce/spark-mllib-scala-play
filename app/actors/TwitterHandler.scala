package actors

import akka.actor.{Props, Actor}
import org.apache.spark.SparkContext
import play.api.Logger

object TwitterHandler {

  def props(sparkContext: SparkContext) = Props(new TwitterHandler(sparkContext))
}

class TwitterHandler(sparkContext: SparkContext) extends Actor {

  val log = Logger(this.getClass)

  override def receive = {
    case undefined => log.info(s"Unexpected message $undefined")
  }
}
