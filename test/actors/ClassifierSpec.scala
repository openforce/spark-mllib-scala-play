package actors

import actors.Classifier.{ClassificationResult, Classify}
import actors.FetchResponseHandler.FetchResponseTimeout
import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{MustMatchers, WordSpecLike}
import twitter.LabeledTweet
import scala.concurrent.duration._

class ClassifierSpec extends TestKit(ActorSystem("ClassifierSpecAS")) with ImplicitSender with WordSpecLike with MustMatchers {

  "A classifier" should {

    "return a list of classified tweets" in {

      val sc = createSparkContext()

      val twitterHandler = system.actorOf(Props[TwitterHandlerProxyStub], "twitter-handler")
      val onlineTrainer = system.actorOf(Props[OnlineTrainerProxyStub], "online-trainer")
      val batchTrainer = system.actorOf(Props[BatchTrainerProxyStub], "batch-trainer")
      val eventServer = system.actorOf(Props[EventServerProxyStub], "event-server")

      val estimator = new EstimatorProxyStub()

      val classifier = system.actorOf(Props(new Classifier(sc, twitterHandler, onlineTrainer, batchTrainer, eventServer, estimator)))

      val probe = TestProbe()

      within(1 seconds) {
        probe.send(classifier, Classify("apple"))
        val result = probe.expectMsgType[ClassificationResult]
        val labeledTweets = Array(LabeledTweet("The new Apple iPhone 6s is awesome", "1.0"), LabeledTweet("Apple is overpriced.", "0.0"))
        val onlineModelResult, batchModelResult = labeledTweets

        result.batchModelResult must equal(batchModelResult)
        result.onlineModelResult must equal(onlineModelResult)
      }
    }

    "return a TimeoutException when timeout is exceeded" in {

      val sc = createSparkContext()

      val actorNamePrefix = "timing-out"
      val twitterHandler = system.actorOf(Props[TimingOutTwitterHandlerProxyStub], s"$actorNamePrefix-twitter-handler")
      val onlineTrainer = system.actorOf(Props[OnlineTrainerProxyStub], s"$actorNamePrefix-online-trainer")
      val batchTrainer = system.actorOf(Props[BatchTrainerProxyStub], s"$actorNamePrefix-batch-trainer")
      val eventServer = system.actorOf(Props[EventServerProxyStub], s"$actorNamePrefix-event-server")

      val estimator = new EstimatorProxyStub()

      val classifier = system.actorOf(Props(new Classifier(sc, twitterHandler, onlineTrainer, batchTrainer, eventServer, estimator)))

      val probe = TestProbe()

      within(2 second, 3 seconds) {
        probe.send(classifier, Classify("apple"))
        probe.expectMsg(FetchResponseTimeout)
      }
    }
  }

  def createSparkContext(): SparkContext = {
    val conf = new SparkConf().setAppName("test").setMaster("local")
      .set("spark.driver.allowMultipleContexts", "true")
    val sc = new SparkContext(conf)
    sc
  }
}
