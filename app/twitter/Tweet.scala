package twitter

import features._
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import play.api.libs.json.Json
import twitter4j.Status
import util.SentimentIdentifier._

case class LabeledTweet(tweet: String, sentiment: String)

object LabeledTweet {

  implicit val formats = Json.format[LabeledTweet]

}

case class Tweet(text: String, sentiment: Double) extends Serializable {

  def tokens(implicit transformer: Transformer): Seq[String] = transformer.transform(text)

  def toLabeledPoint(implicit hashingTF: HashingTF, transformer: Transformer): LabeledPoint = LabeledPoint(sentiment, hashingTF.transform(tokens))

  def toLabeledPoint(f: String => Vector): LabeledPoint = LabeledPoint(sentiment, f(text))

}


object Tweet {

  def apply(status: Status): Tweet = Tweet(
    status.getText,
    if (isPositive(status.getText)) 1.0 else 0.0
  )

  def apply(tweetText: String): Tweet = Tweet(
    tweetText,
    if (isPositive(tweetText)) 1.0 else 0.0
  )

}