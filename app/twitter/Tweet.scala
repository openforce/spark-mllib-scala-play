package twitter

import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import twitter4j.Status

case class LabeledTweet(tweet: String, sentiment: String)

abstract class Tweet extends Serializable with Transformable {

  final val positive = ":)"
  final val negative = ":("

  def text: String

  def sentiment: Double

  def features(implicit hashingTF: HashingTF): Vector = hashingTF.transform(unigramsAndBigrams(text))

  def toLabeledPoint(implicit hashingTF: HashingTF): LabeledPoint = LabeledPoint(sentiment, features)

}


object Tweet {

  def apply(status: Status): Tweet = new Tweet {
    override val text: String = status.getText
    override val sentiment: Double = if (text.contains(positive)) 1.0 else 0.0
  }

  def apply(tweetText: String): Tweet = new Tweet {
    override val text: String = tweetText
    override val sentiment: Double = if (text.contains(positive)) 1.0 else 0.0
  }

  def apply(tweetText: String, label: Double): Tweet = new Tweet {
    override def text: String = tweetText
    override def sentiment: Double = label
  }
}