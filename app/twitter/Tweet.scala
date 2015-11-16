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

abstract class Tweet extends Serializable with Transformable {

  import Tweet._

  val text: String

  def sentiment: Double

  def features(implicit hashingTF: HashingTF): Vector = hashingTF.transform(tokens)

  def tokens = unigramsAndBigrams(text)

  def toLabeledPoint(implicit hashingTF: HashingTF): LabeledPoint = LabeledPoint(sentiment, features)

  def toLabeledPoint(f: String => Vector): LabeledPoint = LabeledPoint(sentiment, f(text))

  override def toString = text

}


object Tweet {

  implicit val languagePack: LanguagePack = chalk.text.LanguagePack.English

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