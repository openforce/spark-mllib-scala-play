package actors

import actors.OnlineTrainer.{Init, GetLatestModel}
import akka.actor.{Actor, ActorRef, Props}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import twitter4j.auth.OAuthAuthorization

object OnlineTrainer {

  def props(sparkContext: SparkContext) = Props(new OnlineTrainer(sparkContext))

  case object GetLatestModel

  case object Init

}

class OnlineTrainer(sparkContext: SparkContext) extends Actor {

  val log = Logger(this.getClass)

  val smileys = Seq(":)", ":(")

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  val coefficients = 100

  var model: StreamingLogisticRegressionWithSGD = _

  self ! Init

  override def receive = {
    case Init =>
      log.debug("Init online trainer")
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = smileys).filter(t => t.getUser.getLang == "en" && !t.isRetweet).map { tweet =>
        val text = tweet.getText
        val sentiment = if (text.contains(":)")) 1.0 else 0.0
        val tokens = text.split("\\W+")
        val tf = new HashingTF(100)
        LabeledPoint(
          label = sentiment,
          features = tf.transform(tokens)
        )
      }
      model = new StreamingLogisticRegressionWithSGD()
        .setInitialWeights(Vectors.zeros(coefficients))
      model.trainOn(stream)
      ssc.start()

    case GetLatestModel =>
      sender ! model.latestModel()
  }

}
