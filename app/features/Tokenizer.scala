package features

trait Tokenizer extends Function1[String, Seq[String]]

object UnigramTokenizer extends Tokenizer {

  override def apply(sentence: String): Seq[String] =
    sentence
      .split(" ")
      .map(_.replaceAll("""\W+""", ""))

}

object BigramTokenizer extends Tokenizer {

  override def apply(sentence: String): Seq[String] =
    sentence
      .split("\\.")
      .map(_.trim)
      .map(UnigramTokenizer(_).sliding(2))
      .flatMap(identity).map(_.mkString(" ")).toSeq

}