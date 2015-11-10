package features

import org.apache.spark.mllib.feature.{HashingTF, IDF, IDFModel}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import play.api.Play.{configuration, current}
import twitter.Tweet

object Features {

  val coefficients = configuration.getInt("ml.features.coefficients").getOrElse(1500)

}