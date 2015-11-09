package actors

import actors.Messages.{Unsubscribe, Subscribe}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive

object EventServer {

  def props = Props[EventServer]

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

    case Unsubscribe =>
      context.unwatch(sender)
      clients -= sender

  }

}
