package actors

import actors.Classifier._
import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import models.CorpusItem
import org.apache.spark.SparkContext
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.feature.{StringIndexer, Tokenizer, Word2Vec}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter.LinguisticTransformer
import scala.collection.immutable.Queue
import scala.concurrent.duration._

object Classifier {

  def props(sparkContext: SparkContext, vectorizer: ActorRef, twitterHandler: ActorRef) = Props(new Classifier(sparkContext, vectorizer, twitterHandler))

  case class Train(corpus: RDD[CorpusItem])

  case class Predict(token: String)

  case class PredictResult(tweet: String, sentiment: String)

  case class PredictResults(result: Array[PredictResult])

  case object Dequeue

}

class Classifier(sparkContext: SparkContext, vectorizer: ActorRef, twitterHandler: ActorRef) extends Actor {

  implicit val timeout = Timeout(5.seconds)

  val log = Logger(this.getClass)
  val sqlContext = new SQLContext(sparkContext)

  import sqlContext.implicits._

  var pendingQueue = Queue.empty[Predict]

  val linguisticTransformer = new LinguisticTransformer()
    .setInputCol("tweet")
    .setOutputCol("normTweet")

  val tokenizer = new Tokenizer()
    .setInputCol("normTweet")
    .setOutputCol("tokens")

  val indexer = new StringIndexer()
    .setInputCol("sentiment")
    .setOutputCol("label")

  /**
   * Params and defaults:
   *
   * maxIter: maximum number of iterations (>= 0) (default: 1)
   * minCount: the minimum number of times a token must appear to be included in the word2vec model's vocabulary (default: 5)
   * numPartitions: number of partitions for sentences of words (default: 1)
   * outputCol: output column name (default: w2v_2521010012f2__output, current: features)
   * seed: random seed (default: -1961189076)
   * stepSize: Step size to be used for each iteration of optimization. (default: 0.025)
   * vectorSize: the dimension of codes after transforming from words (default: 100)
   */
  val word2Vec = new Word2Vec()
    .setInputCol("tokens")
    .setOutputCol("features")

  /**
   * Params and defaults:
   *
   * featuresCol: features column name (default: features)
   * fitIntercept: whether to fit an intercept term (default: true)
   * labelCol: label column name (default: label)
   * maxIter: maximum number of iterations (>= 0) (default: 100)
   * predictionCol: prediction column name (default: prediction)
   * probabilityCol: Column name for predicted class conditional probabilities. Note: Not all models output well-calibrated probability estimates! These probabilities should be treated as confidences, not precise probabilities. (default: probability)
   * rawPredictionCol: raw prediction (a.k.a. confidence) column name (default: rawPrediction)
   * regParam: regularization parameter (>= 0) (default: 0.0)
   * standardization: whether to standardize the training features before fitting the model. (default: true)
   * threshold: threshold in binary classification prediction, in range [0, 1] (default: 0.5)
   * thresholds: Thresholds in multi-class classification to adjust the probability of predicting each class. Array must have length equal to the number of classes, with values >= 0. The class with largest value p/t is predicted, where p is the original probability of that class and t is the class' threshold. (undefined)
   * tol: the convergence tolerance for iterative algorithms (default: 1.0E-6)
   */
  val lr = new LogisticRegression()

  val pipeline = new Pipeline()
    .setStages(Array(indexer, linguisticTransformer, tokenizer, word2Vec, lr))

  override def receive = training

  def training: Receive = {

    case Train(corpus: RDD[CorpusItem]) => {

      log.info(s"Start training")

      val data: DataFrame = corpus
        .toDF
        .filter("sentiment in ('positive', 'negative')")

      val splits = data.randomSplit(Array(0.7, 0.3), 42)
      val train = splits(0)
      val test = splits(1)

      val model = pipeline.fit(train)

      var total = 0.0
      var correct = 0.0

      model.transform(test)
        .select("tweet", "sentiment", "label", "probability", "prediction")
        .collect()
        .foreach { case Row(tweet, sentiment, label, prob, prediction) =>
          if (label == prediction) correct += 1
          total += 1
          log.info(s"'$tweet': '$sentiment' ($label) --> prediction=$prediction ($prob)")
        }

      val precision = correct / total

      log.info(s"precision: ${precision}")

      context.become(predicting(model))

      self ! Dequeue
    }

    case msg@Predict(token) => pendingQueue = pendingQueue enqueue msg

  }

  def predicting(model: PipelineModel): Receive = {

    case Dequeue =>
      while (pendingQueue.nonEmpty) {
        val (predict, newQ) = pendingQueue.dequeue
        self ! predict
        pendingQueue = newQ
      }

    case Predict(token: String) =>
      log.info(s"Start predicting")
      val client = sender
      (twitterHandler ? Fetch(token)).mapTo[FetchResult] map { fr =>
        val results = model
          .transform(sparkContext.parallelize(fr.tweets.map(t => CorpusItem("", t))).toDF())
          .select("tweet", "prediction")
          .collect()
          .map { case Row(tweet: String, prediction: Double) =>
            PredictResult(tweet, prediction.toString)
          }
        client ! PredictResults(results)
      }

  }

}
