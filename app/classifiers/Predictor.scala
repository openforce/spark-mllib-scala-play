package classifiers

import actors.Classifier.Point
import actors.TwitterHandler.FetchResponse
import org.apache.spark.SparkContext
import org.apache.spark.ml.Transformer
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, Row}
import twitter.{LabeledTweet, Tweet}
import features.Transformers.default._

trait PredictorProxy {

  def predict(batchTrainingModel: Transformer, fetchResponse: FetchResponse): Array[LabeledTweet]

  def predict(onlineTrainingModel: LogisticRegressionModel, onlineFeatures: RDD[(String, Vector)]): Array[LabeledTweet]

}

class Predictor(sparkContext: SparkContext) extends PredictorProxy {

  val sqlContext = new SQLContext(sparkContext)
  import sqlContext.implicits._

  override def predict(batchTrainingModel: Transformer, fetchResponse: FetchResponse) =
    batchTrainingModel
      .transform(fetchResponse.tweets.map(t => Point(t, Tweet(t).tokens.toSeq)).toDF())
      .select("tweet","prediction")
      .collect()
      .map { case Row(tweet: String, prediction: Double) =>
        LabeledTweet(tweet, prediction.toString)
      }

  override def predict(onlineTrainingModel: LogisticRegressionModel, onlineFeatures: RDD[(String, Vector)]) =
    onlineFeatures.map { case (tweet, vector) =>
      LabeledTweet(tweet, onlineTrainingModel.predict(vector).toString)
    } collect()

}
