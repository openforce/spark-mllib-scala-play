package actors

import akka.actor._
import akka.event.LoggingReceive

object EventServer {

  def props = Props[EventServer]

}

trait EventServerProxy extends Actor

class EventServer extends Actor with ActorLogging with EventServerProxy {

  var clients = Set.empty[ActorRef]

  def receive = LoggingReceive {

    case msg: String => clients.foreach(_ ! msg)

    case Subscribe =>
      context.watch(sender)
      clients += sender

    case Unsubscribe =>
      context.unwatch(sender)
      clients -= sender

  }

}
