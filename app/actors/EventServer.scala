package actors

import actors.EventServer.Subscribe
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive

object EventServer {

  def props = Props[EventServer]

  case object Subscribe
}

class EventServer extends Actor with ActorLogging {

  var clients = Set.empty[ActorRef]

  def receive = LoggingReceive {

    case msg: String =>
      clients.foreach { c =>
        c ! msg
      }

    case Subscribe =>
      context.watch(sender)
      clients += sender

  }

}
