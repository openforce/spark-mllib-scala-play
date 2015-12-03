package features

import org.apache.spark.mllib.feature.{IDF, HashingTF}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import twitter.Tweet
import features.Transformers.default._

case class TfIdf(corpus: RDD[Tweet]) extends Serializable {

  import Features._

  val tf = new HashingTF(coefficients)

  val idf = new IDF().fit(tf.transform(corpus.map(_.tokens)))

  def tf(text: Seq[String]): Vector = tf.transform(text)

  def tfIdf(text: Seq[String]): Vector = idf.transform(tf.transform(text))

}