package actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.json.JsValue

object EventListener {
  def props(out: ActorRef, eventServer: ActorRef) = Props(new EventListener(out, eventServer))
}

class EventListener(out: ActorRef, actor: ActorRef) extends Actor {

  override def preStart() = {
    actor ! Subscribe
  }

  override def postStop(): Unit = {
    super.postStop()

    actor ! Unsubscribe
  }

  def receive = {
    case msg: String =>
      out ! msg

    case msg: JsValue =>
      out ! msg
  }
}