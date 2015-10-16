package features

import org.apache.spark.mllib.feature.IDFModel
import org.apache.spark.mllib.feature.{IDF, HashingTF}
import org.apache.spark.rdd.RDD
import twitter.Tweet
import org.apache.spark.mllib.linalg.Vector

trait TfIdf {

  val coefficients = 100

  implicit val hashingTf = new HashingTF(coefficients)

  var tf: RDD[Vector] = _

  var idf: IDFModel = _

  def train(corpus: RDD[Tweet]) = {
    tf = hashingTf.transform(corpus.map(_.tokens))
    tf.cache()
    idf = new IDF().fit(tf)
  }

  def tfidf(text: Set[String]) = idf.transform(hashingTf.transform(text))

}
