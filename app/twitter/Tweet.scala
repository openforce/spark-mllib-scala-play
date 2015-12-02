package twitter

import features._
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import play.api.libs.json.Json
import twitter4j.Status
import util.SentimentIdentifier._
import cats.std.all._
import cats.syntax.functor._

case class LabeledTweet(tweet: String, sentiment: String)

object LabeledTweet {

  implicit val formats = Json.format[LabeledTweet]

}

case class Tweet(text: String, sentiment: Double, transformer: String => Seq[String]) extends Serializable  {

  def tokens: Seq[String] = transformer(text)

  def features(implicit hashingTF: HashingTF): Vector = hashingTF.transform(tokens)

  def toLabeledPoint(implicit hashingTF: HashingTF): LabeledPoint = LabeledPoint(sentiment, features)

  def toLabeledPoint(f: String => Vector): LabeledPoint = LabeledPoint(sentiment, f(text))

}


object Tweet {

  val sentiment: String => String = SentimentNormalizable$
  val shorty: String => String = ShortFormNormalizable$
  val noise: String => String = NoiseNormalizable$
  val stemmer: String => String = EnglishStemmer
  val unigram: String => Seq[String] = UnigramTokenizer
  val bigram: String => Seq[String] = BigramTokenizer
  val tokenizer: String => Seq[String] = (sentence: String) => unigram(sentence) ++ bigram(sentence)

  val transformer: String => Seq[String] = sentiment andThen shorty andThen noise andThen stemmer andThen tokenizer

  def apply(status: Status): Tweet = Tweet(
    status.getText,
    if (isPositive(status.getText)) 1.0 else 0.0,
    transformer
  )

  def apply(tweetText: String): Tweet = Tweet(
    tweetText,
    if (isPositive(tweetText)) 1.0 else 0.0,
    transformer
  )

  def apply(text: String, sentiment: Double): Tweet = Tweet(
    text,
    sentiment,
    transformer
  )

}