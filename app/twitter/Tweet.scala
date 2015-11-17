package twitter

import chalk.text.LanguagePack
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

case class Tweet(text: String, sentiment: Double) extends Serializable with Transformable {

  implicit val languagePack: LanguagePack = chalk.text.LanguagePack.English

  def features(implicit hashingTF: HashingTF): Vector = hashingTF.transform(tokens)

  def tokens = unigramsAndBigrams(text)

  def toLabeledPoint(implicit hashingTF: HashingTF): LabeledPoint = LabeledPoint(sentiment, features)

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