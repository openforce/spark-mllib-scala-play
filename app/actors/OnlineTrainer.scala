package actors

import actors.TwitterHandler.FetchResult
import akka.actor.{Actor, Props}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, StreamingLogisticRegressionWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.feature._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, Duration, StreamingContext}
import play.api.Logger
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization

import scala.util.Random

object OnlineTrainer {

  val smileys = Seq(":)", ":(")

  val coefficients = 100

  implicit val hashingTf = new HashingTF(coefficients)

  def props(sparkContext: SparkContext) = Props(new OnlineTrainer(sparkContext))

  case object GetLatestModel

  case object Init

  case class Train(corpus: RDD[Tweet])

  case class GetFeatures(fetchResult: FetchResult)

  var lrModel: StreamingLogisticRegressionWithSGD = _

  var word2Vec: Word2VecModel = _

  var idf: IDFModel = _

  var corpus: RDD[Tweet] = _

  private def tfidf(text: Set[String]) = idf.transform(hashingTf.transform(text))

}

class OnlineTrainer(sparkContext: SparkContext) extends Actor {

  import OnlineTrainer._

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  self ! Init

  override def receive = {

    case Train(crps) =>
      println(s"Received train with stream")
      corpus = crps
      lrModel = new StreamingLogisticRegressionWithSGD()
        .setInitialWeights(Vectors.zeros(coefficients))
      word2Vec = new Word2Vec()
        .fit(corpus.map(_.tokens))
      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = smileys)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map { Tweet(_) }
        .map(tweet =>
          tweet.toLabeledPoint { _ => tfidf(tweet.tokens)}
//          tweet.toLabeledPoint { _ =>
//            // Creating a representation of those words for sentiment analysis is as simple as adding together the feature vectors for each
//            // word in the document and dividing that vector by the number of word vectors extracted from the document.
//            Vectors.tweet.tokens.map(s => new org.jblas.DoubleMatrix(word2Vec.transform(s))).reduce((v1,v2) => v1.add(v2))
//          }
        )
      val tf = hashingTf.transform(corpus.map(_.tokens))
      tf.cache()
      idf = new IDF().fit(tf)
      lrModel.trainOn(stream)
      ssc.start()

    case GetFeatures(fetchResult) =>
      val rdd: RDD[String] = sparkContext.parallelize(fetchResult.tweets)
      rdd.cache()
      val features = rdd map { t => tfidf(Tweet(t).tokens) }
      sender ! (rdd, features)

    case GetLatestModel =>
      val lr = lrModel.latestModel()
      sender ! lr
      testOn(lr)

  }

  private def testOn(model: LogisticRegressionModel): Unit = {
    val scoreAndLabels = corpus map { tweet =>
      (model.predict(tfidf(tweet.tokens)), tweet.sentiment)
    }
    val total: Double = scoreAndLabels.count()
    val metrics = new BinaryClassificationMetrics(scoreAndLabels)
    log.debug(s"Current model: ${model.toString()}")
    log.debug(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
    val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
    val accuracy = correct / total
    log.debug(s"Accuracy: $accuracy ($correct of $total)")
  }


}
