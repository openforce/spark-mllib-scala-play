package actors

import actors.Director.BatchTrainingFinished
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.apache.spark.SparkContext
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.HashingTF
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.ml.{Pipeline, Transformer}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SQLContext}
import twitter.Tweet

object BatchTrainer {

  def props(sparkContext: SparkContext, receptionist: ActorRef) = Props(new BatchTrainer(sparkContext, receptionist: ActorRef))

  case class BatchTrainerModel(model: Option[Transformer])

  case class BatchFeatures(features: Option[RDD[(String, Vector)]])

}

trait BatchTrainerProxy extends Actor

class BatchTrainer(sparkContext: SparkContext, receptionist: ActorRef) extends Actor with ActorLogging with BatchTrainerProxy {

  import BatchTrainer._

  var model: Transformer = _

  val sqlContext = new SQLContext(sparkContext)

  import sqlContext.implicits._

  override def receive = {

    case Train(corpus: RDD[Tweet]) =>
      log.debug(s"Received Train message with tweets corpus")
      log.info(s"Start batch training")
      val data: DataFrame = corpus.map(t => (t.text, t.sentiment, t.tokens.toSeq)).toDF("tweet", "label", "tokens")
      val hashingTF = new HashingTF()
        .setInputCol("tokens")
        .setOutputCol("features")
      val lr = new LogisticRegression()
        .setMaxIter(10)
      val pipeline = new Pipeline()
        .setStages(Array(hashingTF, lr))
      val paramGrid = new ParamGridBuilder()
        .addGrid(hashingTF.numFeatures, Array(10, 100, 1000))
        .addGrid(lr.regParam, Array(0.1, 0.01))
        .build()
      val cv = new CrossValidator()
        .setEstimator(pipeline)
        .setEvaluator(new BinaryClassificationEvaluator)
        .setEstimatorParamMaps(paramGrid)
        .setNumFolds(2)
      model = cv.fit(data).bestModel
      log.info("Batch training finished")

      receptionist ! BatchTrainingFinished

    case GetLatestModel =>
      log.debug(s"Received GetLatestModel message")
      sender ! BatchTrainerModel(Some(model))
      log.debug(s"Returned model $model")

  }

}
