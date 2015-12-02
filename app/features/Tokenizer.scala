package features

trait Tokenizer extends Function1[String, Seq[String]]

object Tokenizer {

  def unigram = new Tokenizer {
    override def apply(sentence: String): Seq[String] =
      sentence
        .split(" ")
        .map(_.replaceAll("""\W+""", ""))
  }

  def ngram(n: Int) = new Tokenizer {
    override def apply(sentence: String): Seq[String] =
      sentence
        .split("\\.")
        .map(_.trim)
        .map(unigram(_).sliding(n))
        .flatMap(identity).map(_.mkString(" ")).toSeq
  }

  def bigram = ngram(2)

  def trigram = ngram(3)

}