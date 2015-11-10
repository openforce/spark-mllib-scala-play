package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.OnlineTrainer.OnlineTrainerModel
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import features.TfIdf
import org.apache.spark.SparkContext
import org.apache.spark.mllib.evaluation.{MulticlassMetrics, BinaryClassificationMetrics}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import play.api.libs.json.{JsValue, Json}
import twitter.Tweet

object StatisticsServer extends TfIdf {

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
  var corpus: RDD[Tweet] = _
  var dfCorpus: DataFrame = _

  import sqlContext.implicits._

  override def receive = LoggingReceive {

    case m: BatchTrainerModel => testBatchModel(m)

    case m: OnlineTrainerModel => testOnlineModel(m)

    case c: RDD[Tweet] => {
      corpus = c

      train(corpus)
      dfCorpus = c.map(t => {
        (t.tokens.toSeq, t.sentiment)
      }).toDF("tokens", "label")
    }

    case msg: JsValue => sendMessage(msg)

    case Subscribe =>
      context.watch(sender)
      clients += sender

    case Unsubscribe =>
      context.unwatch(sender)
      clients -= sender

  }

  private def testOnlineModel(model: OnlineTrainerModel) = {
    model.model.foreach(model => {
      val scoreAndLabels = corpus map { tweet => (model.predict(tfidf(tweet.tokens)), tweet.sentiment) }
      val total: Double = scoreAndLabels.count()
      val metrics = new BinaryClassificationMetrics(scoreAndLabels)
      val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
      val accuracy = correct / total

      val statistics = new Statistics(metrics.areaUnderROC(), accuracy)

      log.info(s"Current model: ${model.toString()}")
      log.info(s"Area under the ROC curve: ${metrics.areaUnderROC()}")
      log.info(s"Accuracy: $accuracy ($correct of $total)")
      val mc = new MulticlassMetrics(scoreAndLabels)
      log.info(s"Precision: ${mc.precision}")
      log.info(s"Recall: ${mc.recall}")
      log.info(s"F-Measure: ${mc.fMeasure}")

      sendMessage(Json.toJson(statistics))
    })
  }

  private def sendMessage(msg: JsValue) = {
    clients.foreach { c =>
      c ! msg
    }
  }

  private def testBatchModel(model: BatchTrainerModel) = {
    model.model.foreach(model => {
      var total = 0.0
      var correct = 0.0

      model
        .transform(dfCorpus)
        .select("tokens", "label", "probability", "prediction")
        .collect()
        .foreach { case Row(tokens, label, prob, prediction) =>
        if (label == prediction) correct += 1
        total += 1
      }

      val accuracy = correct / total
      log.info(s"Batch accuracy: ${accuracy}")

      sendMessage(Json.toJson(new Statistics(0.0, accuracy)))
    })
  }

}
