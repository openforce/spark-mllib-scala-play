package features

import org.apache.spark.mllib.feature.{HashingTF, IDF, IDFModel}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import play.api.Play.{configuration, current}
import twitter.Tweet

trait TfIdf {

  val coefficients = configuration.getInt("ml.features.coefficients").getOrElse(1500)

  implicit val hashingTf = new HashingTF(coefficients)

  var hashingTF: RDD[Vector] = _

  var idf: IDFModel = _

  def train(corpus: RDD[Tweet]) = {
    hashingTF = hashingTf.transform(corpus.map(_.tokens))
    hashingTF.cache()
    idf = new IDF().fit(hashingTF)
  }

  def tf(text: Set[String]): Vector = hashingTf.transform(text)

  def tfidf(text: Set[String]): Vector = idf.transform(hashingTf.transform(text))

}
