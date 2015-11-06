package actors

import actors.Messages.Subscribe
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import play.api.libs.json.{JsValue}

object StatisticsServer {

  def props = Props[EventServer]

}

class StatisticsServer extends Actor with ActorLogging {

  var clients = Set.empty[ActorRef]

  def receive = LoggingReceive {

    case msg: JsValue =>
      clients.foreach { c =>
        c ! msg
      }

    case Subscribe =>
      context.watch(sender)
      clients += sender

  }

}
