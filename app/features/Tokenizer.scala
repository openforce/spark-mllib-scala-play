package features

import chalk.text.LanguagePack

trait Tokenizer extends Function1[String, Seq[String]]

trait EnglishSentenceTokenizer extends Tokenizer {

  implicit val languagePack: LanguagePack = chalk.text.LanguagePack.English

  override def apply(sentence: String): Seq[String] =
    sentence
      .split(" ")
      .map(languagePack.stemmer.getOrElse(identity[String] _))

}

object BigramTokenizer extends EnglishSentenceTokenizer {

  override def apply(sentence: String): Seq[String] = {
    super.apply(sentence) ++
      sentence
        .split("\\.")
        .map((s) => super.apply(s).sliding(2))
        .flatMap(identity).map(_.mkString(" ")).toSeq
  }

}