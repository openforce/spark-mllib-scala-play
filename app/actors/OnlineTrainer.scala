package actors

import actors.Director.{OnlineTrainerRestarted, OnlineTrainingFinished}
import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.event.LoggingReceive
import features.Transformers.default._
import features.{Features, TfIdf}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.scheduler.{StreamingListener, StreamingListenerReceiverError, StreamingListenerReceiverStopped}
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Play.{configuration, current}
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier

object OnlineTrainer {

  def props(sparkContext: SparkContext, director: ActorRef) = Props(new OnlineTrainer(sparkContext, director))

  val dumpCorpus = configuration.getBoolean("ml.corpus.dump").getOrElse(false)

  val dumpPath = configuration.getString("ml.corpus.path").getOrElse("")

  case class OnlineTrainerModel(model: Option[LogisticRegressionModel])

  case class OnlineFeatures(features: Option[RDD[(String, Vector)]])

}

trait OnlineTrainerProxy extends Actor

class OnlineTrainer(sparkContext: SparkContext, director: ActorRef) extends Actor with ActorLogging with OnlineTrainerProxy {

  import OnlineTrainer._

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  val sqlContext = new SQLContext(sparkContext)

  var logisticRegression: Option[StreamingLogisticRegressionWithSGD] = None

  var maybeTfIdf: Option[TfIdf] = None

  import sqlContext.implicits._

  override def postStop() = {
    log.debug("Stopping streaming context")
    ssc.stop(false, true)
  }

  override def preStart() = {
    log.debug(s"Pre-Start online-trainer")
  }

  override def postRestart(reason: Throwable) = {
    super.postRestart(reason)
    log.debug(s"Post-Restart online-trainer due to $reason")
    context.parent ! OnlineTrainerRestarted
  }

  override def receive = LoggingReceive {

    case Train(corpus) =>
      log.debug(s"Received Train message with tweets corpus")
      if (dumpCorpus) corpus.map(t => (t.tokens, t.sentiment)).toDF().write.parquet(dumpPath)
      val tfIdf = TfIdf(corpus)
      maybeTfIdf = Some(tfIdf)
      logisticRegression = Some(new StreamingLogisticRegressionWithSGD()
        .setNumIterations(200)
        .setInitialWeights(Vectors.zeros(Features.coefficients))
        .setStepSize(1.0))
      log.info(s"Start twitter stream for online training")
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_) }
        .map(tweet => tweet.toLabeledPoint { _ => tfIdf.tf(tweet.tokens)})
      logisticRegression.map(lr => lr.trainOn(stream))
      ssc.addStreamingListener(new StreamingListener {
        override def onReceiverStopped(receiverStopped: StreamingListenerReceiverStopped) = {
          log.debug(s"Stream receiver stopped")
          self ! Kill
        }
        })
      ssc.start()
      director ! OnlineTrainingFinished

    case GetFeatures(fetchResponse) =>
      log.debug(s"Received GetFeatures message")
      val features = maybeTfIdf map { tfIdf =>
        val rdd: RDD[String] = sparkContext.parallelize(fetchResponse.tweets)
        rdd.cache()
        rdd map { t => (t, tfIdf.tfIdf(Tweet(t).tokens)) }
      }
      sender ! OnlineFeatures(features)

    case GetLatestModel =>
      log.debug(s"Received GetLatestModel message")
      val maybeModel = logisticRegression.map(_.latestModel())
      sender ! OnlineTrainerModel(maybeModel)

  }

}
