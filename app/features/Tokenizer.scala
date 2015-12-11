package features

trait Tokenizer extends Function1[String, Seq[String]]

object Tokenizer {

  def ngram(n: Int) = new Tokenizer {
    override def apply(sentence: String): Seq[String] =
      sentence
        .split("\\.")
        .map(_.trim)
        .map(unigram(_).sliding(n))
        .flatMap(identity).map(_.mkString(" ")).toSeq
  }

  val unigram = new Tokenizer {
    override def apply(sentence: String): Seq[String] =
      sentence
        .split(" ")
        .map(_.replaceAll("""\W+""", ""))
  }

  val bigram = ngram(2)

  val trigram = ngram(3)

}