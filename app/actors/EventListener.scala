package actors

import actors.StatisticsServer.Statistics
import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.json.Json


object EventListener {

  def props(out: ActorRef, producer: ActorRef) = Props(new EventListener(out, producer))

}

class EventListener(out: ActorRef, producer: ActorRef) extends Actor {

  override def preStart() = producer ! Subscribe

  override def postStop(): Unit = producer ! Unsubscribe

  def receive = {

    case msg: String => out ! msg

    case msg: Statistics => out ! Json.toJson(msg)

  }

}