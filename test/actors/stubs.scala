package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.EventServer.Subscribe
import actors.OnlineTrainer._
import actors.TwitterHandler.{Fetch, FetchResponse}
import akka.actor.ActorLogging
import classifiers.EstimatorProxy
import org.apache.spark.ml.Transformer
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vector
import twitter.LabeledTweet

class TwitterHandlerProxyStub extends TwitterHandlerProxy with ActorLogging {

  override def receive = {
    case Fetch(keyword) => {
      log.debug(s"Received Fetch message")
      val tweets = Seq("The new Apple iPhone 6s is awesome", "Apple is overpriced.")
      sender ! FetchResponse(keyword, tweets)
    }
  }
}

class OnlineTrainerProxyStub extends OnlineTrainerProxy with ActorLogging {
  override def receive = {
    case GetFeatures(fetchResponse) =>
      log.debug(s"Received GetFeatures message")
      def mockRDD[T]: org.apache.spark.rdd.RDD[T] = null
      val features: RDD[(String, Vector)] = mockRDD[(String, Vector)]
      sender ! OnlineFeatures(Some(features))

    case GetLatestModel =>
      log.debug(s"Received GetLatestModel message")
      val lr: LogisticRegressionModel = null
      sender ! OnlineTrainerModel(Some(lr))
  }
}

class BatchTrainerProxyStub extends BatchTrainerProxy with ActorLogging {
  override def receive = {
    case GetLatestModel =>
      log.debug(s"Received GetLatestModel message")
      val model: Transformer = null
      sender ! BatchTrainerModel(Some(model))
  }
}

class EventServerProxyStub extends EventServerProxy with ActorLogging {
  override def receive = {
    case msg: String =>
      println(msg)

    case Subscribe =>
      println(s"Received subscribe message")

  }
}

class EstimatorProxyStub extends EstimatorProxy {

  val lts = Array(LabeledTweet("The new Apple iPhone 6s is awesome", "1.0"), LabeledTweet("Apple is overpriced.", "0.0"))

  override def predict(batchTrainingModel: Transformer, fetchResponse: FetchResponse): Array[LabeledTweet] = lts

  override def predict(onlineTrainingModel: LogisticRegressionModel, onlineFeatures: RDD[(String, Vector)]): Array[LabeledTweet] = lts
}


