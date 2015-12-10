package actors

import actors.Classifier.{ClassificationResult, Classify}
import actors.FetchResponseHandler.FetchResponseTimeout
import actors.TrainingModelResponseHandler.TrainingModelRetrievalTimeout
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import controllers.OAuthKeys
import org.scalatest.{BeforeAndAfterEach, MustMatchers, WordSpecLike}
import play.api.libs.oauth.{ConsumerKey, RequestToken}
import twitter.LabeledTweet

import scala.concurrent.duration._

class ClassifierSpec extends TestKit(ActorSystem("ClassifierSpecAS"))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterEach
  with SparkTestContext {

  "A classifier" should {

    val oAuthKeys = OAuthKeys(ConsumerKey("",""), RequestToken("",""))

    "return a list of classified tweets" in {

      val twitterHandler = system.actorOf(Props[TwitterHandlerProxyStub], "twitter-handler")
      val onlineTrainer = system.actorOf(Props[OnlineTrainerProxyStub], "online-trainer")
      val batchTrainer = system.actorOf(Props[BatchTrainerProxyStub], "batch-trainer")

      val estimator = new PredictorProxyStub()

      val classifier = system.actorOf(Props(new Classifier(sc, twitterHandler, onlineTrainer, batchTrainer, estimator)))

      val probe = TestProbe()

      within(1 seconds) {
        probe.send(classifier, Classify("apple", oAuthKeys))
        val result = probe.expectMsgType[ClassificationResult]
        val labeledTweets = Array(LabeledTweet("The new Apple iPhone 6s is awesome", "1.0"), LabeledTweet("Apple is overpriced.", "0.0"))
        val onlineModelResult, batchModelResult = labeledTweets

        result.batchModelResult must equal(batchModelResult)
        result.onlineModelResult must equal(onlineModelResult)
      }
    }

    "return a FetchResponseTimeout when timeout in twitter handler is exceeded" in {

      val actorNamePrefix = "twitter-timing-out"
      val twitterHandler = system.actorOf(Props[TimingOutTwitterHandlerProxyStub], s"$actorNamePrefix-twitter-handler")
      val onlineTrainer = system.actorOf(Props[OnlineTrainerProxyStub], s"$actorNamePrefix-online-trainer")
      val batchTrainer = system.actorOf(Props[BatchTrainerProxyStub], s"$actorNamePrefix-batch-trainer")

      val estimator = new PredictorProxyStub()

      val classifier = system.actorOf(Props(new Classifier(sc, twitterHandler, onlineTrainer, batchTrainer, estimator)))

      val probe = TestProbe()

      within(2 second, 3 seconds) {
        probe.send(classifier, Classify("apple", oAuthKeys))
        probe.expectMsg(FetchResponseTimeout)
      }
    }



    "return a TrainingModelRetrievalTimeout when timeout in training model response handler is exceeded" in {

      val actorNamePrefix = "trainer-timing-out"
      val twitterHandler = system.actorOf(Props[TwitterHandlerProxyStub], s"$actorNamePrefix-twitter-handler")
      val onlineTrainer = system.actorOf(Props[TimingOutOnlineTrainerProxyStub], s"$actorNamePrefix-online-trainer")
      val batchTrainer = system.actorOf(Props[BatchTrainerProxyStub], s"$actorNamePrefix-batch-trainer")

      val estimator = new PredictorProxyStub()

      val classifier = system.actorOf(Props(new Classifier(sc, twitterHandler, onlineTrainer, batchTrainer, estimator)))

      val probe = TestProbe()

      within(3 second, 4 seconds) {
        probe.send(classifier, Classify("apple", oAuthKeys))
        probe.expectMsg(4 seconds, TrainingModelRetrievalTimeout)
      }
    }

  }


}
