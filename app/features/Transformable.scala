package features

import chalk.text.LanguagePack
import scala.util.matching.Regex

trait Transformable[T] extends Serializable {

  def mapping: Map[T, String]

  def transformFn(sentence: String): PartialFunction[(T, String), String]

  def transform(sentence: String): String = {
    var s = sentence.toLowerCase
    for(pair <- mapping) s = transformFn(sentence)(pair)
    s
  }

}

object SentimentTransformable extends Transformable[String] {

  val good = " good "
  val bad = " bad "
  val sad = " sad "

  override def mapping = Map(
    // positive emoticons
    "&lt;3" -> good,
    " ->d" -> good,
    " ->dd" -> good,
    "8)" -> good,
    " ->-)" -> good,
    " ->)" -> good,
    ";)" -> good,
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

  override def transformFn(sentence: String): PartialFunction[(String, String), String] = {
    case (emoji, sentiment) =>
      sentence.replace(emoji, sentiment)
  }

}

object ShortFormTransformable extends Transformable[Regex] {

  override def mapping = Map(
    "\br\b".r -> "you",
    "\bhaha\b".r -> "ha",
    "\bhahaha\b".r -> "ha",
    "\bdon't\b".r -> "do not",
    "\bdoesn't\b".r -> "does not",
    "\bdidn't\b".r -> "did not",
    "\bhasn't\b".r -> "has not",
    "\bhaven't\b".r -> "have not",
    "\bhadn't\b".r -> "had not",
    "\bwon't\b".r -> "will not",
    "\bwouldn't\b".r -> "would not",
    "\bcan't\b".r -> "can not",
    "\bcannot\b".r -> "can not"
  )

  override def transformFn(sentence: String): PartialFunction[(Regex, String), String] = {
    case (shortForm, extendedForm) =>
      shortForm.replaceAllIn(sentence, extendedForm)
  }

}

object NoiseTransformable extends Transformable[Regex] {

  override def mapping = Map(
    "([a-z])\\1\\1+".r -> "$1$1", // shorten duplicate chars
    "@\\S+".r -> "USERNAME",      // replace username
    "http:\\/\\/\\S+".r -> "URL",  // replace url
    "[-_]".r -> " "
  )

  override def transformFn(sentence: String): PartialFunction[(Regex, String), String] = {
    case (noise, antiNoise) =>
      noise.replaceAllIn(sentence, antiNoise)
  }

}

//trait Xxx {
//  def transformSentence(text: String): String = {
//    var t = text.toLowerCase
//    for ((emo, repl) <- emoRepl) t = t.replace(emo, repl)
//    for ((regex, repl) <- reRepl) t = regex.replaceAllIn(t, repl)
//    t.replace("-", " ").replace("_", " ")
//  }

//  def shortenDuplicateChars(word: String): String = word.replaceAll("([a-z])\\1\\1+", "$1$1")
//
//  def username(word: String): String = word.replaceAll("@\\S+", "USERNAME")
//
//  def url(word: String): String = word.replaceAll("http:\\/\\/\\S+", "URL")

//  def unigrams(text: String)(implicit languagePack: LanguagePack): Set[String] =
//    tokenizeSentence(text).toSet
//
//  def bigrams(text: String)(implicit languagePack: LanguagePack): Set[String] =
//    text
//      .split("\\.")
//      .map { s => tokenizeSentence(s).sliding(2) }
//      .flatMap(identity).map(_.mkString(" ")).toSet

//}

//object Transformable extends Transformable