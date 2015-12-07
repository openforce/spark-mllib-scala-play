package actors

import actors.Director.OnlineTrainingFinished
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import features.{Features, TfIdf}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.feature.{StandardScalerModel, HashingTF, StandardScaler}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Play.{configuration, current}
import twitter.{TwitterHelper, Tweet}
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier
import features.Transformers.default._

object OnlineTrainer {

  def props(sparkContext: SparkContext, director: ActorRef) = Props(new OnlineTrainer(sparkContext, director: ActorRef))

  val dumpCorpus = configuration.getBoolean("ml.corpus.dump").getOrElse(false)

  val dumpPath = configuration.getString("ml.corpus.path").getOrElse("")

  case class OnlineTrainerModel(model: Option[LogisticRegressionModel])

  case class OnlineFeatures(features: Option[RDD[(String, Vector)]])

}

trait OnlineTrainerProxy extends Actor

class OnlineTrainer(sparkContext: SparkContext, director: ActorRef) extends Actor with ActorLogging with OnlineTrainerProxy {

  import OnlineTrainer._

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHelper.config))

  val sqlContext = new SQLContext(sparkContext)

  var logisticRegression: Option[StreamingLogisticRegressionWithSGD] = None

  import TfIdf._

  import sqlContext.implicits._

  override def postStop() = ssc.stop(false)

  override def receive = training

  def training = LoggingReceive {

    case Train(corpus) =>
      log.debug(s"Received Train message with tweets corpus")
      if (dumpCorpus) corpus.map(t => (t.tokens, t.sentiment)).toDF().write.parquet(dumpPath)
      val scalerModel = new StandardScaler(withMean = true, withStd = true).fit(corpus.map(t => t.features.toDense))
      logisticRegression = Some(new StreamingLogisticRegressionWithSGD().setInitialWeights(Vectors.zeros(Features.coefficients)))
      log.info(s"Start twitter stream for online training")
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map(Tweet(_).toLabeledPoint)
        .map(point => LabeledPoint(point.label, scalerModel.transform(point.features.toDense)))
      logisticRegression foreach (_.trainOn(stream))
      ssc.start()
      context.become(predicting(scalerModel))
      director ! OnlineTrainingFinished

  }

  def predicting(scalerModel: StandardScalerModel) = LoggingReceive {

    case GetFeatures(fetchResponse) =>
      log.debug(s"Received GetFeatures message")
      val rdd = sparkContext
        .parallelize(fetchResponse.tweets)
        .map(text => (text, scalerModel.transform(Tweet(text).features.toDense)))
      sender ! OnlineFeatures(Some(rdd))

    case GetLatestModel =>
      log.debug(s"Received GetLatestModel message")
      val maybeModel = logisticRegression.map(_.latestModel())
      sender ! OnlineTrainerModel(maybeModel)

  }

}
