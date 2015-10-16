package actors

import actors.TwitterHandler.FetchResult
import akka.actor.{Actor, Props}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.feature._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier

object OnlineTrainer {

  val coefficients = 100

  implicit val hashingTf = new HashingTF(coefficients)

  def props(sparkContext: SparkContext) = Props(new OnlineTrainer(sparkContext))

  case object GetLatestModel

  case class Train(corpus: Seq[Tweet])

  case class GetFeatures(fetchResult: FetchResult)

  var lrModel: StreamingLogisticRegressionWithSGD = _

  var word2Vec: Word2VecModel = _

  var idf: IDFModel = _

  var corpus: RDD[Tweet] = _

  private def tfIdf(text: Set[String]) = idf.transform(hashingTf.transform(text))

}

class OnlineTrainer(sparkContext: SparkContext) extends Actor {

  import OnlineTrainer._

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  override def receive = {

    case Train(tweets) =>
      log.info(s"Received corpus with ${tweets.size} tweets to train")
      corpus = sparkContext.makeRDD(tweets)
      lrModel = new StreamingLogisticRegressionWithSGD()
        .setInitialWeights(Vectors.zeros(coefficients))
      word2Vec = new Word2Vec()
        .fit(corpus.map(_.tokens))
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_) }
        .map(tweet => tweet.toLabeledPoint { _ => tfIdf(tweet.tokens)})

      val tf = hashingTf.transform(corpus.map(_.tokens))
      tf.cache()
      idf = new IDF().fit(tf)
      lrModel.trainOn(stream)
      ssc.start()

    case GetFeatures(fetchResult) =>
      val rdd: RDD[String] = sparkContext.parallelize(fetchResult.tweets)
      rdd.cache()
      val features = rdd map { t => tfIdf(Tweet(t).tokens) }
      sender ! (rdd, features)

    case GetLatestModel =>
      val lr = lrModel.latestModel()
      sender ! lr
      testOn(lr)

  }

  private def testOn(model: LogisticRegressionModel): Unit = {
    val scoreAndLabels = corpus map { tweet =>
      (model.predict(tfIdf(tweet.tokens)), tweet.sentiment)
    }
    val total: Double = scoreAndLabels.count()
    val metrics = new BinaryClassificationMetrics(scoreAndLabels)
    println(s"Current model: ${model.toString()}")
    println(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
    val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
    val accuracy = correct / total
    println(s"Accuracy: $accuracy ($correct of $total)")
  }
}
