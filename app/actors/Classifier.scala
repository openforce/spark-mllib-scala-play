package actors

import actors.Classifier._
import actors.TwitterHandler.{FetchResult, Fetch}
import akka.actor.{ActorRef, Actor, Props}
import akka.util.Timeout
import models.CorpusItem
import org.apache.spark.SparkContext
import org.apache.spark.ml.{Model, Pipeline}
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.{Tokenizer, StringIndexer, Word2Vec}
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import play.api.Logger
import twitter.LinguisticTransformer
import akka.pattern._
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

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

      val linguisticTransformer = new LinguisticTransformer()
        .setInputCol("tweet")
        .setOutputCol("normTweet")

      val tokenizer = new Tokenizer()
        .setInputCol("normTweet")
        .setOutputCol("tokens")

      val indexer = new StringIndexer()
        .setInputCol("sentiment")
        .setOutputCol("label")

      val word2Vec = new Word2Vec()
        .setInputCol("tokens")
        .setOutputCol("features")

      val lr = new LogisticRegression()

      val pipeline = new Pipeline()
        .setStages(Array(indexer, linguisticTransformer, tokenizer, word2Vec, lr))

      val paramGrid = new ParamGridBuilder()
        .addGrid(word2Vec.vectorSize, Array(100, 300))
        .addGrid(word2Vec.minCount, Array(0, 5))
        .addGrid(lr.regParam, Array(0.01, 0.1, 1.0))
        .addGrid(lr.maxIter, Array(10))
        .build()

      val cv = new CrossValidator()
        .setNumFolds(10)
        .setEstimator(pipeline)
        .setEstimatorParamMaps(paramGrid)
        .setEvaluator(new BinaryClassificationEvaluator)

      val cvModel = cv.fit(train)

      var total = 0.0
      var correct = 0.0

      cvModel.transform(test)
        .select("tweet", "sentiment", "label", "probability", "prediction")
        .collect()
        .foreach { case Row(tweet, sentiment, label, prob, prediction) =>
          if (label == prediction) correct += 1
          total += 1
          log.info(s"'$tweet': '$sentiment' ($label) --> prediction=$prediction ($prob)")
        }

      val precision = correct / total
      log.info(s"precision: ${precision}")

      context.become(predicting(cvModel.bestModel))

      self ! Dequeue
    }

    case msg@Predict(token) => pendingQueue = pendingQueue enqueue msg

  }

  def predicting(model: Model[_]): Receive = {

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
        val res = model
          .transform(sparkContext.parallelize(fr.tweets.map(t => CorpusItem("", t)))
          .toDF())
          .select("tweet", "prediction")
          .collect()
          .map { case Row(tweet: String, sentiment: String) => PredictResult(tweet, sentiment) }
        client ! res
      }

  }

}
