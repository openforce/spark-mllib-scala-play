package actors

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import play.api.libs.json.JsValue

object EventListener {
  def props(out: ActorRef, eventServer: ActorRef) = Props(new EventListener(out, eventServer))
}

class EventListener(out: ActorRef, eventServer: ActorRef) extends Actor {

  override def preStart() = {
    eventServer ! Subscribe
  }

  override def postStop(): Unit = {
    eventServer ! Unsubscribe
  }

  def receive = LoggingReceive {
    case msg: String =>
      out ! msg

    case msg: JsValue =>
      out ! msg
  }
}