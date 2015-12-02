package features

import chalk.text.LanguagePack

trait Tokenizer {

  def tokenize(sentence: String): Seq[String]

}

trait EnglishSentenceTokenizer extends Tokenizer {

  implicit val languagePack: LanguagePack = chalk.text.LanguagePack.English

  override def tokenize(sentence: String): Seq[String] =
    sentence
      .split(" ")
      .map(languagePack.stemmer.getOrElse(identity[String] _))
      .map(_.replaceAll("""\W+""", "")).toSeq

}

trait BigramTokenizer extends EnglishSentenceTokenizer {

  override def tokenize(sentence: String): Seq[String] =
    super.tokenize(sentence)
//      .map { s => tokenizeSentence(s).sliding(2) }
//      .flatMap(identity).map(_.mkString(" ")).toSet

}