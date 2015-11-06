package actors

import actors.Messages.Subscribe
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import features.TfIdf
import org.apache.spark.SparkContext
import org.apache.spark.ml.Model
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import play.api.libs.json.{JsValue, Json}
import twitter.Tweet

object StatisticsServer extends TfIdf {

  var corpus: RDD[Tweet] = _

  def props(sparkContext: SparkContext) = Props(new StatisticsServer(sparkContext))

  case class Statistics(roc: Double, accuracy: Double)

  object Statistics {
    implicit val formatter = Json.format[Statistics]
  }

}

class StatisticsServer(sparkContext: SparkContext) extends Actor with ActorLogging {

  import StatisticsServer._

  val sqlContext = new SQLContext(sparkContext)
  var clients = Set.empty[ActorRef]

  import sqlContext.implicits._

  override def receive = LoggingReceive {

    case m: Model[_] => testBatchModel(m)

    case m: LogisticRegressionModel => testOnlineModel(m)

    case c: RDD[Tweet] => corpus = c

    case msg: JsValue => sendMessage(msg)

    case Subscribe =>
      context.watch(sender)
      clients += sender

  }

  private def testOnlineModel(model: LogisticRegressionModel) = {
    val scoreAndLabels = corpus map { tweet => (model.predict(tfidf(tweet.tokens)), tweet.sentiment) }
    val total: Double = scoreAndLabels.count()
    val metrics = new BinaryClassificationMetrics(scoreAndLabels)
    log.info(s"Current model: ${model.toString()}")
    log.info(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
    val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
    val accuracy = correct / total
    log.info(s"Accuracy: $accuracy ($correct of $total)")

    val statistics = new Statistics(metrics.areaUnderROC(), accuracy)

    sendMessage(Json.toJson(statistics))
  }

  private def testBatchModel(model: Model[_]) = {
    var total = 0.0
    var correct = 0.0

    val df = corpus.map(t => {
      (t.tokens.toSeq, t.sentiment)
    }).toDF()

    model
      .transform(df)
      .select("tweet", "features", "label", "probability", "prediction")
      .collect()
      .foreach { case Row(tweet, features, label, prob, prediction) =>
      if (label == prediction) correct += 1
      total += 1
      log.info(s"'$tweet': ($label) --> prediction=$prediction ($prob)")
    }

    val precision = correct / total

    sendMessage(Json.toJson(new Statistics(0.0, precision)))
  }

  private def sendMessage(msg: JsValue) = {
    clients.foreach { c =>
      c ! msg
    }
  }

}
