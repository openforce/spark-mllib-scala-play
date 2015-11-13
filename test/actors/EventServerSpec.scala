package actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{MustMatchers, WordSpecLike}
import scala.concurrent.duration._

class EventServerSpec extends TestKit(ActorSystem("ClassifierSpecAS")) with ImplicitSender with WordSpecLike with MustMatchers {

  "A event server" should {

    "handle subscribe messages" in {

      val eventServer = system.actorOf(EventServer.props)

      val eventMsg = "Some event occurred"

      within(250 milliseconds) {
        eventServer ! Subscribe
        eventServer ! eventMsg
        expectMsg(eventMsg)
      }
    }

    "handle unsubscribe messages" in {

      val eventServer = system.actorOf(EventServer.props)

      val eventMsg = "Some event occurred"

      within(250 milliseconds) {
        eventServer ! Subscribe
        eventServer ! Unsubscribe
        expectNoMsg()
      }
    }

    "handle " in {

      val eventServer = system.actorOf(EventServer.props)

      val eventMsg = "Some event occurred"

      within(250 milliseconds) {
        eventServer ! Subscribe
        eventServer ! Unsubscribe
        eventServer ! eventMsg
        expectNoMsg()
      }
    }
  }

}
