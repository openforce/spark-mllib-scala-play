package actors

import actors.OnlineTrainer.{ValidateOn, Init, GetLatestModel}
import akka.actor.{Actor, ActorRef, Props}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.feature.{Normalizer, StandardScaler, HashingTF}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import twitter.{Tweet, LabeledTweet}
import twitter4j.auth.OAuthAuthorization

import scala.util.Random

object OnlineTrainer {

  val smileys = Seq(":)", ":(")

  val coefficients = 100

  implicit val hashingTf = new HashingTF(coefficients)

  def props(sparkContext: SparkContext) = Props(new OnlineTrainer(sparkContext))

  case object GetLatestModel

  case object Init

  case class ValidateOn(corpus: RDD[LabeledTweet])

}

class OnlineTrainer(sparkContext: SparkContext) extends Actor {

  import OnlineTrainer._

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  var model: StreamingLogisticRegressionWithSGD = _

  var corpus: Option[RDD[LabeledTweet]] = None

  self ! Init

  override def receive = {

    case Init =>
      log.debug("Init online trainer")
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = smileys)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_).toLabeledPoint }
      model = new StreamingLogisticRegressionWithSGD()
        .setInitialWeights(Vectors.dense(Array.tabulate(coefficients)(_ => Random.nextDouble())))
      model.trainOn(stream)
      ssc.start()

    case GetLatestModel =>
      val lr = model.latestModel()
      sender ! lr
      testOn(lr)

    case ValidateOn(corpus) =>
      log.debug("OnlineTrainer now validates on new corpus")
      this.corpus = Some(corpus)

  }

  private def testOn(model: LogisticRegressionModel): Unit =
    corpus foreach { testData =>
      val scoreAndLabels = testData map { lt =>
        val tweet = Tweet(lt.tweet, if (lt.sentiment == "positive") 1.0 else 0.0)
        (model.predict(tweet.features), tweet.sentiment)
      }
      val total: Double = scoreAndLabels.count()
      val metrics = new BinaryClassificationMetrics(scoreAndLabels)
      log.debug(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
      val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
      val accuracy = correct / total
      log.debug(s"Accuracy: $accuracy")
    }

}
