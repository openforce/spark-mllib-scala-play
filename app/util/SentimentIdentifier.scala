package util

import twitter.Tweet

import scala.util.matching.Regex

object SentimentIdentifier {

  val positiveEmoticons = Seq(":)", ":-)", ";-)", ";)")
  val negativeEmoticons = Seq(":(", ":-(")
  val sentimentEmoticons = positiveEmoticons ++ negativeEmoticons

  private val positivePattern = new Regex(s"(${positiveEmoticons.map(_.replace(")", """\)""")).mkString("|")})+")
  private val negativePattern = new Regex(s"(${negativeEmoticons.map(_.replace("(", """\(""")).mkString("|")})+")

  def isPositive(text: String): Boolean = positivePattern.findFirstIn(text).isDefined
  def isPositive(tweet: Tweet): Boolean = isPositive(tweet.text)
  def isNegative(text: String): Boolean = negativePattern.findFirstIn(text).isDefined
  def isNegative(tweet: Tweet): Boolean = isNegative(tweet.text)

  def isNeutral(text: String) = !isPositive(text) && !isNegative(text)

}
