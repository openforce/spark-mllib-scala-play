package twitter

import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import util.SentimentIdentifier._
import twitter4j.Status

case class LabeledTweet(tweet: String, sentiment: String)

abstract class Tweet extends Serializable with Transformable {

  val text: String

  def sentiment: Double

  def features(implicit hashingTF: HashingTF): Vector = {
    val tks: Set[String] = tokens
    println(s"raw: $text\n\nunigramsAndBigrams: $tks")
    hashingTF.transform(tks)
  }

  def tokens = unigramsAndBigrams(text)

  def toLabeledPoint(implicit hashingTF: HashingTF): LabeledPoint = LabeledPoint(sentiment, features)

  def toLabeledPoint(f: String => Vector): LabeledPoint = LabeledPoint(sentiment, f(text))

  override def toString = text
}


object Tweet {

  def apply(status: Status): Tweet = new Tweet {
    override val text: String = status.getText
    override val sentiment: Double = if (isPositive(text)) 1.0 else 0.0
  }

  def apply(tweetText: String): Tweet = new Tweet {
    override val text: String = tweetText
    override val sentiment: Double = if (isPositive(text)) 1.0 else 0.0
  }

  def apply(tweetText: String, label: Double): Tweet = new Tweet {
    override val text: String = tweetText
    override val sentiment: Double = label
  }
}