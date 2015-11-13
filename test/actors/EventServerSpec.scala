package actors

import akka.actor.{PoisonPill, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{MustMatchers, WordSpecLike}
import scala.concurrent.duration._

class EventServerSpec extends TestKit(ActorSystem("ClassifierSpecAS")) with ImplicitSender with WordSpecLike with MustMatchers {

  val eventMsg = "Some event occurred"

  "A event server" should {


    "handle subscribe messages" in {

      val eventServer = system.actorOf(EventServer.props)

      within(250 milliseconds) {
        eventServer ! Subscribe
        eventServer ! eventMsg
        expectMsg(eventMsg)
      }
    }

    "handle unsubscribe messages" in {

      val eventServer = system.actorOf(EventServer.props)

      within(250 milliseconds) {
        eventServer ! Subscribe
        eventServer ! Unsubscribe
        eventServer ! eventMsg
        expectNoMsg()
      }
    }

    "handle death of server" in {

      val eventServer = system.actorOf(EventServer.props)

      val probe = TestProbe()

      within(250 milliseconds) {
        probe.watch(eventServer)
        eventServer ! Subscribe
        eventServer ! PoisonPill
        probe.expectTerminated(eventServer)
      }
    }
  }

}
