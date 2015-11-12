package actors

import actors.Director.OnlineTrainingFinished
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import features.{Features, TfIdf}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Play.{configuration, current}
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier

object OnlineTrainer {

  def props(sparkContext: SparkContext, receptionist: ActorRef) = Props(new OnlineTrainer(sparkContext, receptionist: ActorRef))

  val dumpCorpus = configuration.getBoolean("ml.corpus.dump").getOrElse(false)

  val dumpPath = configuration.getString("ml.corpus.path").getOrElse("")

  case class OnlineTrainerModel(model: Option[LogisticRegressionModel])

  case class OnlineFeatures(features: Option[RDD[(String, Vector)]])

}

trait OnlineTrainerProxy extends Actor

class OnlineTrainer(sparkContext: SparkContext, receptionist: ActorRef) extends Actor with ActorLogging with OnlineTrainerProxy {

  import OnlineTrainer._

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  val sqlContext = new SQLContext(sparkContext)

  var logisticRegression: StreamingLogisticRegressionWithSGD = _

  import sqlContext.implicits._

  override def receive = {

    case Train(corpus) =>
      log.debug(s"Received Train message with tweets corpus")
      if (dumpCorpus) corpus.map(t => (t.tokens.toSeq, t.sentiment)).toDF().write.parquet(dumpPath)
      val tfIdf = TfIdf(corpus)
      logisticRegression = new StreamingLogisticRegressionWithSGD()
        .setNumIterations(200)
        .setInitialWeights(Vectors.zeros(Features.coefficients))
        .setStepSize(1.0)
      log.info(s"Start twitter stream for online training")
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_) }
        .map(tweet => tweet.toLabeledPoint { _ => tfIdf.tf(tweet.tokens)})
      logisticRegression.trainOn(stream)
      ssc.start()
      receptionist ! OnlineTrainingFinished

    case GetFeatures(fetchResponse) =>
      log.debug(s"Received GetFeatures message")
      val rdd: RDD[String] = sparkContext.parallelize(fetchResponse.tweets)
      rdd.cache()
      val features = rdd map { t => (t, TfIdf.getInstance.tfIdf(Tweet(t).tokens)) }
      sender ! OnlineFeatures(Some(features))

    case GetLatestModel =>
      log.debug(s"Received GetLatestModel message")
      val lr = logisticRegression.latestModel()
      sender ! OnlineTrainerModel(Some(lr))

  }

}
