package features

import scala.util.matching.Regex

trait Normalizable[T] extends Function1[String, String] with Serializable {

  val mapping: Map[T, String]

  def transformFn(sentence: String): (T, String) => String

  def transform(sentence: String): PartialFunction[(T, String), String] = { case (l, r) => transformFn(sentence)(l, r) }

  override def apply(sentence: String): String = {
    var s = sentence.toLowerCase
    for(pair <- mapping)
      s = transform(s)(pair)
    s
  }

}

object SentimentNormalizer extends Normalizable[String] {

  // Data cleaning inspired by Luis Pedro Coelho and Willi Richert's great book: https://www.packtpub.com/big-data-and-business-intelligence/building-machine-learning-systems-python-second-edition

  val good = "good"
  val bad = "bad"
  val sad = "sad"

  val mapping = Map(
    // positive emoticons
    ":)" -> good,
    ":-)" -> good,
    "&lt;3" -> good,
    " ->d" -> good,
    " ->dd" -> good,
    "8)" -> good,
    " ->-)" -> good,
    " ->)" -> good,
    ";)" -> good,
    ";-)" -> good,
    "(- ->" -> good,
    "( ->" -> good,
    "\uD83D\uDE03" -> good, // smiley open mouth
    "\uD83D\uDE04" -> good, // smile
    "\uD83D\uDE0A" -> good, // smiling face with smiling eyes
    "\uD83D\uDE00" -> good, // grin
    "\uD83D\uDE17" -> good, // kiss
    "\uD83D\uDE1B" -> good, // stuck-out-tongue
    "\u263A" -> good, // relaxed
    "\u270C" -> good, // victory
    "\u2764" -> good, // heart
    "\u2B50" -> good, // star
    "\uD83D\uDC4D" -> good, // thumbs up
    "\uD83D\uDC4C" -> good, // ok hand
    "\uD83D\uDC4F" -> good, // clapping hands
    "\uD83D\uDE02" -> good, // tears of joy
    "\uD83D\uDE06" -> good, // laughing
    "\uD83D\uDE0B" -> good, // yum
    "\uD83D\uDE0D" -> good, // heart eyes
    // negative emoticons
    "\uD83D\uDE1F" -> bad, // worried
    "\uD83D\uDE26" -> bad, // frowning
    "\uD83D\uDE27" -> bad, // anguished
    "\uD83D\uDE15" -> bad, // confused
    "\uD83D\uDE1E" -> bad, // disappointed
    "\uD83D\uDE20" -> bad, // angry
    "\uD83D\uDE22" -> sad, // cry
    "\uD83D\uDE21" -> sad, // rage
    " ->/" -> bad,
    " ->&gt;" -> sad,
    " ->')" -> sad,
    " ->-(" -> bad,
    " ->(" -> bad,
    " ->S" -> bad,
    " ->-S" -> bad
  )

  override def transformFn(sentence: String) = (emoji, sentiment) => sentence.replace(emoji, sentiment)

}

object ShortFormNormalizer extends Normalizable[Regex] {

  val mapping = Map(
    "\\byou're\\b".r -> "you are",
    "\\bu\\b".r -> "you",
    "\\bhaha\\b".r -> "ha",
    "\\bhahaha\\b".r -> "ha",
    "\\bdon't\\b".r -> "do not",
    "\\bdoesn't\\b".r -> "does not",
    "\\bdidn't\\b".r -> "did not",
    "\\bhasn't\\b".r -> "has not",
    "\\bhaven't\\b".r -> "have not",
    "\\bhadn't\\b".r -> "had not",
    "\\bwon't\\b".r -> "will not",
    "\\bwouldn't\\b".r -> "would not",
    "\\bcan't\\b".r -> "can not",
    "\\bcannot\\b".r -> "can not"
  )

  override def transformFn(sentence: String) = (shortForm, extendedForm) => shortForm.replaceAllIn(sentence, extendedForm)

}

object NoiseNormalizer extends Normalizable[Regex] {

  val mapping = Map(
    "([a-z])\\1\\1+".r -> "$1$1", // shorten duplicate chars
    "@\\S+".r -> "username",      // replace username
    "http:\\/\\/\\S+".r -> "url",  // replace url
    "[-_]".r -> " "
  )

  override def transformFn(sentence: String) = (noise, signal) => noise.replaceAllIn(sentence, signal)

}