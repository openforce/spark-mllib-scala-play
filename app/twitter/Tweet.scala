package twitter

import chalk.text.LanguagePack
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

abstract class Tweet(text: String, sentiment: Double) extends Serializable with Tokenizer {

  def tokens: Seq[String] = tokenize(
    NoiseTransformable.transform(
      ShortFormTransformable.transform(
        SentimentTransformable.transform(text))))

  def features(implicit hashingTF: HashingTF): Vector = hashingTF.transform(tokens)

  def toLabeledPoint(implicit hashingTF: HashingTF): LabeledPoint = LabeledPoint(sentiment, features)

  def toLabeledPoint(f: String => Vector): LabeledPoint = LabeledPoint(sentiment, f(text))

}


object Tweet {

  def apply(status: Status): Tweet = new Tweet(
    status.getText,
    if (isPositive(status.getText)) 1.0 else 0.0
  ) with BigramTokenizer

  def apply(tweetText: String): Tweet = new Tweet(
    tweetText,
    if (isPositive(tweetText)) 1.0 else 0.0
  ) with BigramTokenizer

}