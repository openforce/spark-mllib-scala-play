package actors

import actors.Receptionist.TrainingFinished
import akka.actor.{ActorRef, ActorLogging, Actor, Props}
import features.TfIdf
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.evaluation.{BinaryClassificationMetrics, MulticlassMetrics}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import play.api.Play.{configuration, current}
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier

object OnlineTrainer extends TfIdf {

  def props(sparkContext: SparkContext, receptionist: ActorRef) = Props(new OnlineTrainer(sparkContext, receptionist: ActorRef))

  var logisticRegression: StreamingLogisticRegressionWithSGD = _

  var corpus: RDD[Tweet] = _

  val dumpCorpus = configuration.getBoolean("ml.corpus.dump").getOrElse(false)

  val dumpPath = configuration.getString("ml.corpus.path").getOrElse("")

}

class OnlineTrainer(sparkContext: SparkContext, receptionist: ActorRef) extends Actor with ActorLogging {

  import OnlineTrainer._

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  val sqlContext = new SQLContext(sparkContext)

  import sqlContext.implicits._

  override def receive = {

    case Train(tweets) =>
      log.info(s"Received corpus with tweets to train")
      corpus = tweets
      if (dumpCorpus) corpus.map(t => (t.tokens.toSeq, t.sentiment)).toDF().write.parquet(dumpPath)
      train(corpus)
      logisticRegression = new StreamingLogisticRegressionWithSGD()
        .setNumIterations(200)
        .setInitialWeights(Vectors.zeros(coefficients))
        .setStepSize(1.0)
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_) }
        .map(tweet => tweet.toLabeledPoint { _ => tf(tweet.tokens)})
      logisticRegression.trainOn(stream)
      ssc.start()

      receptionist ! TrainingFinished

    case GetFeatures(fetchResult) =>
      val rdd: RDD[String] = sparkContext.parallelize(fetchResult.tweets)
      rdd.cache()
      val features = rdd map { t => (t, tfidf(Tweet(t).tokens)) }
      sender ! features

    case GetLatestModel =>
      val lr = logisticRegression.latestModel()
      sender ! lr
      testOn(lr)

  }

  private def testOn(model: LogisticRegressionModel): Unit = {
    val scoreAndLabels = corpus map { tweet => (model.predict(tfidf(tweet.tokens)), tweet.sentiment) }
    val total: Double = scoreAndLabels.count()
    val metrics = new BinaryClassificationMetrics(scoreAndLabels)
    log.info(s"Current model: ${model.toString()}")
    log.info(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
    val correct: Double = scoreAndLabels.map { case ((score, label)) => if (score == label) 1 else 0 }.reduce(_+_)
    val accuracy = correct / total
    log.info(s"Accuracy: $accuracy ($correct of $total)")
    val mc = new MulticlassMetrics(scoreAndLabels)
    log.info(s"Precision: ${mc.precision}")
    log.info(s"Recall: ${mc.recall}")
    log.info(s"F-Measure: ${mc.fMeasure}")
  }

}
