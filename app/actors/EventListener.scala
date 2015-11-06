package actors

import actors.Messages.Subscribe
import akka.actor.{Actor, Props, ActorRef}

object EventListener {
  def props(out: ActorRef, eventServer: ActorRef) = Props(new EventListener(out, eventServer))
}

class EventListener(out: ActorRef, actor: ActorRef) extends Actor {

  override def preStart() = {
    actor ! Subscribe
  }

  def receive = {
    case msg: String =>
      out ! msg
  }
}