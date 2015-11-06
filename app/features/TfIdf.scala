package features

import org.apache.spark.mllib.feature.{Normalizer, IDFModel, IDF, HashingTF}
import org.apache.spark.rdd.RDD
import twitter.Tweet
import org.apache.spark.mllib.linalg.Vector
import play.api.Play.{configuration, current}

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

  def tf(text: Set[String]) = hashingTf.transform(text)

  def tfidf(text: Set[String]) = idf.transform(hashingTf.transform(text))

}
