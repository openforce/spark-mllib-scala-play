package twitter

trait Transformable extends Serializable {

  private final val emoRepl = Map(
    // positive emoticons
    "&lt;3" -> " good ",
    " ->d" -> " good ",
    " ->dd" -> " good ",
    "8)" -> " good ",
    " ->-)" -> " good ",
    " ->)" -> " good ",
    ";)" -> " good ",
    "(- ->" -> " good ",
    "( ->" -> " good ",
    // negative emoticons
    " ->/" -> " bad ",
    " ->&gt;" -> " sad ",
    " ->')" -> " sad ",
    " ->-(" -> " bad ",
    " ->(" -> " bad ",
    " ->S" -> " bad ",
    " ->-S" -> " bad "
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

  def tokenizeSentence(sentence: String): Seq[String] =
    transformSentence(sentence)
      .split(" ")
      .map(_.toLowerCase)
      .map(shortenDuplicateChars)
      .map(username)
      .map(url)
      .map(_.replaceAll("""\W+""", "")).toSeq

  def unigramsAndBigrams(text: String): Set[String] =
    (tokenizeSentence(text) ++ // unigrams
      text // bigrams
        .split("\\.")
        .map { s => tokenizeSentence(s).sliding(2) }
        .flatMap(identity).map(_.mkString(" "))).toSet

}