package twitter

import chalk.text.LanguagePack

trait Transformable extends Serializable {

  private val good = " good "
  private val bad = " bad "
  private val sad = " sad "
  private final val emoRepl = Map(
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

  private final val reRepl = Map(
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

  def transformSentence(text: String): String = {
    var t = text.toLowerCase
    for ((emo, repl) <- emoRepl) t = t.replace(emo, repl)
    for ((regex, repl) <- reRepl) t = regex.replaceAllIn(t, repl)
    t.replace("-", " ").replace("_", " ")
  }

  def shortenDuplicateChars(word: String): String = word.replaceAll("([a-z])\\1\\1+", "$1$1")

  def username(word: String): String = word.replaceAll("@\\S+", "USERNAME")

  def url(word: String): String = word.replaceAll("http:\\/\\/\\S+", "URL")

  def tokenizeSentence(sentence: String)(implicit languagePack: LanguagePack): Seq[String] =
    transformSentence(sentence)
      .split(" ")
      .map(_.toLowerCase)
      .map(shortenDuplicateChars)
      .map(username)
      .map(url)
      .map(languagePack.stemmer.getOrElse(identity[String] _))
      .map(_.replaceAll("""\W+""", "")).toSeq

  def unigramsAndBigrams(text: String)(implicit languagePack: LanguagePack): Set[String] =
    (tokenizeSentence(text) ++ // unigrams
      text // bigrams
        .split("\\.")
        .map { s => tokenizeSentence(s).sliding(2) }
        .flatMap(identity).map(_.mkString(" "))).toSet

}

object Transformable extends Transformable