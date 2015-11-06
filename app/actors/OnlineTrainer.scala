package actors

import actors.TwitterHandler.FetchResult
import akka.actor.{ActorRef, Actor, Props}
import features.TfIdf
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.feature._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import play.api.libs.json.Json
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier

object OnlineTrainer extends TfIdf {

  def props(sparkContext: SparkContext) = Props(new OnlineTrainer(sparkContext))

  case object GetLatestModel

  case object GetStatistics

  case class Statistics(roc: Double, accuracy: Double)

  object Statistics {
    implicit val formatter = Json.format[Statistics]
  }


  case class Train(corpus: RDD[Tweet])

  case class GetFeatures(fetchResult: FetchResult)

  var logisticRegression: StreamingLogisticRegressionWithSGD = _

  var corpus: RDD[Tweet] = _

}

class OnlineTrainer(sparkContext: SparkContext) extends Actor {

  import OnlineTrainer._

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  override def receive = {

    case Train(tweets) =>
      log.info(s"Received corpus with tweets to train")
      //      corpus = sparkContext.makeRDD(tweets)
      corpus = tweets
      train(corpus)
      logisticRegression = new StreamingLogisticRegressionWithSGD()
        .setInitialWeights(Vectors.zeros(coefficients))
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_) }
        .map(tweet => tweet.toLabeledPoint { _ => tfidf(tweet.tokens)})
      logisticRegression.trainOn(stream)
      ssc.start()

    case GetFeatures(fetchResult) =>
      val rdd: RDD[String] = sparkContext.parallelize(fetchResult.tweets)
      rdd.cache()
      val features = rdd map { t => (t, tfidf(Tweet(t).tokens)) }
      sender ! features

    case GetLatestModel =>
      val lr = logisticRegression.latestModel()
      sender ! lr
      testOn(lr)

    case GetStatistics =>
      sender ! testOn(logisticRegression.latestModel())

  }

  private def testOn(model: LogisticRegressionModel): Statistics = {
    val scoreAndLabels = corpus map { tweet => (model.predict(tfidf(tweet.tokens)), tweet.sentiment) }
    val total: Double = scoreAndLabels.count()
    val metrics = new BinaryClassificationMetrics(scoreAndLabels)
    log.info(s"Current model: ${model.toString()}")
    log.info(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
    val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
    val accuracy = correct / total
    log.info(s"Accuracy: $accuracy ($correct of $total)")

    Statistics(metrics.areaUnderROC(), accuracy)
  }

}


