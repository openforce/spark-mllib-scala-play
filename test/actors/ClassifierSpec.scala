package actors

import actors.Classifier.{ClassificationResult, Classify}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{MustMatchers, WordSpecLike}
import twitter.LabeledTweet
import scala.concurrent.duration._

class ClassifierSpec extends TestKit(ActorSystem("ClassifierSpecAS")) with ImplicitSender with WordSpecLike with MustMatchers {

  "A classifier" should {

    "return a list of classified tweets" in {

      val probe1 = TestProbe()
      val probe2 = TestProbe()

      val conf = new SparkConf().setAppName("test").setMaster("local")
        .set("spark.driver.allowMultipleContexts", "true")
      val sc = new SparkContext(conf)

      val twitterHandler = system.actorOf(Props[TwitterHandlerProxyStub], "twitter-handler")
      val onlineTrainer = system.actorOf(Props[OnlineTrainerProxyStub], "online-trainer")
      val batchTrainer = system.actorOf(Props[BatchTrainerProxyStub], "batch-trainer")
      val eventServer = system.actorOf(Props[EventServerProxyStub], "event-server")

      val estimator = new EstimatorProxyStub()

      val classifier = system.actorOf(Props(new Classifier(sc, twitterHandler, onlineTrainer, batchTrainer, eventServer, estimator)))

      within(4 seconds) {
        probe1.send(classifier, Classify("apple"))
        val result = probe1.expectMsgType[ClassificationResult]
        val onlineModelResult: Array[LabeledTweet] = Array(LabeledTweet("The new Apple iPhone 6s is awesome", "1.0"), LabeledTweet("Apple is overpriced.", "0.0"))
        val batchModelResult: Array[LabeledTweet] = Array(LabeledTweet("The new Apple iPhone 6s is awesome", "1.0"), LabeledTweet("Apple is overpriced.", "0.0"))

        result.batchModelResult must equal(batchModelResult)
        result.onlineModelResult must equal(onlineModelResult)
      }
    }
  }

}
