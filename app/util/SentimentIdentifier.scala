package util

import scala.util.matching.Regex

object SentimentIdentifier {

  val positiveEmoticons = Seq(":)", ":-)", ";-)", ";)")
  val negativeEmoticons = Seq(":(", ":-(")
  val sentimentEmoticons = positiveEmoticons ++ negativeEmoticons

  private val positivePattern = new Regex(s"(${positiveEmoticons.map(_.replace(")", """\)""")).mkString("|")})+")
  private val negativePattern = new Regex(s"(${negativeEmoticons.map(_.replace("(", """\(""")).mkString("|")})+")

  def isPositive(text: String): Boolean = positivePattern.findFirstIn(text).isDefined
  def isNegative(text: String): Boolean = negativePattern.findFirstIn(text).isDefined

  def isNeutral(text: String) = !isPositive(text) && !isNegative(text)

}
