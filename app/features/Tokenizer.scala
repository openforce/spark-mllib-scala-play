package features

trait Tokenizer extends Function1[String, Seq[String]]

object UnigramTokenizer extends Tokenizer {

  override def apply(sentence: String): Seq[String] =
    sentence
      .split(" ")

}

object BigramTokenizer extends Tokenizer {

  override def apply(sentence: String): Seq[String] =
    sentence
      .split("\\.")
      .map((s) => s.split(" ").sliding(2))
      .flatMap(identity).map(_.mkString(" ")).toSeq

}