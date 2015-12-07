package features

import org.apache.spark.mllib.feature.{IDF, HashingTF}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import twitter.Tweet
import features.Transformers.default._

case class TfIdf(corpus: RDD[Tweet]) extends Serializable {

  private val idf = new IDF().fit(TfIdf.tf.transform(corpus.map(_.tokens)))

  def tf(text: Seq[String]): Vector = TfIdf.tf.transform(text)

  def tfIdf(text: Seq[String]): Vector = idf.transform(TfIdf.tf.transform(text))

}

object TfIdf {

  implicit val tf = new HashingTF(Features.coefficients)

}