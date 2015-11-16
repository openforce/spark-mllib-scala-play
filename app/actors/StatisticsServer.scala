package actors

import actors.BatchTrainer.BatchTrainerModel
import actors.CorpusInitializer.Corpus
import actors.OnlineTrainer.OnlineTrainerModel
import actors.StatisticsServer.TrainerType.TrainerType
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import features.TfIdf
import org.apache.spark.SparkContext
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import play.api.libs.json.{Json, Reads, Writes}
import twitter.Tweet
import util.EnumUtils

object StatisticsServer {

  def props(sparkContext: SparkContext) = Props(new StatisticsServer(sparkContext))

  object TrainerType extends Enumeration {

    type TrainerType = TrainerType.Value

    val Batch, Online = Value

    implicit val reads: Reads[TrainerType] = EnumUtils.enumReads(TrainerType)

    implicit val writes: Writes[TrainerType] = EnumUtils.enumWrites

  }

  case class Statistics(trainer: TrainerType, model: String, areaUnderRoc: Double, accuracy: Double)

  object Statistics {

    implicit val formatter = Json.format[Statistics]

  }

}

class StatisticsServer(sparkContext: SparkContext) extends Actor with ActorLogging {

  import StatisticsServer._

  val sqlContext = new SQLContext(sparkContext)

  var clients = Set.empty[ActorRef]

  var corpus: Corpus = sparkContext.emptyRDD[Tweet]

  var dfCorpus: Option[DataFrame] = None

  import sqlContext.implicits._

  override def receive = LoggingReceive {

    case m: BatchTrainerModel => testBatchModel(m)

    case m: OnlineTrainerModel => testOnlineModel(m)

    case c: Corpus =>
      corpus = c
      dfCorpus = Some(c.map{t => (t.tokens.toSeq, t.sentiment)}.toDF("tokens", "label"))

    case Subscribe =>
      context.watch(sender)
      clients += sender

    case Unsubscribe =>
      context.unwatch(sender)
      clients -= sender

  }

  private def testOnlineModel(onlineTrainerModel: OnlineTrainerModel) =
    onlineTrainerModel.model.map { model =>
      log.debug("Test online trainer model")

      val tfIdf = TfIdf(corpus)
      val scoreAndLabels = corpus map { tweet => (model.predict(tfIdf.tfIdf(tweet.tokens)), tweet.sentiment) }
      val total: Double = scoreAndLabels.count()
      val metrics = new BinaryClassificationMetrics(scoreAndLabels)
      val correct: Double = scoreAndLabels.filter { case ((score, label)) => score == label }.count()
      val accuracy = correct / total

      val statistics = Statistics(TrainerType.Online, model.toString(), metrics.areaUnderROC(), accuracy)
      logStatistics(statistics)

      sendMessage(statistics)
    }

  private def testBatchModel(batchTrainerModel: BatchTrainerModel) =
    for {
      model <- batchTrainerModel.model
      dfCorpus <- dfCorpus
    } yield {
      log.debug("Test batch trainer model")

      val scoreAndLabels = model
        .transform(dfCorpus)
        .select("tokens", "label", "probability", "prediction")
        .map { case Row(tokens, label: Double, probability: Vector, prediction) =>
          (probability(1), label)
        }

      val metrics = new BinaryClassificationMetrics(scoreAndLabels)

      val accuracy = model
        .transform(dfCorpus)
        .select("label", "prediction")
        .map{case Row(label, prediction) => if (label == prediction) 1 else 0}
        .reduce(_+_) / dfCorpus.count()

      val statistics = Statistics(TrainerType.Batch, model.toString(), metrics.areaUnderROC(), accuracy)
      logStatistics(statistics)

      sendMessage(statistics)
    }

  private def sendMessage(msg: Statistics) = clients.foreach { _ ! msg }

  private def logStatistics(statistics: Statistics): Unit = {
    log.info(s"Trainer type: ${statistics.trainer}")
    log.info(s"Current model: ${statistics.model}")
    log.info(s"Area under the ROC curve: ${statistics.areaUnderRoc}")
    log.info(s"Accuracy: ${statistics.accuracy}")
  }

}
