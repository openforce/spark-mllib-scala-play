package actors

import akka.actor.{Actor, Props}
import org.apache.spark.SparkContext
import play.api.Logger

object Vectorizer {

  def props(sparkContext: SparkContext) = Props(new Vectorizer(sparkContext))
}

class Vectorizer(sparkContext: SparkContext) extends Actor {

  val log = Logger(this.getClass)

  override def receive = {
    case undefined => log.info(s"Unexpected message $undefined")
  }
}
