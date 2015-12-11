package features

import chalk.text.LanguagePack

trait Stemmable extends Function1[String, String] with Serializable

object EnglishStemmer extends Stemmable {

  implicit val languagePack: LanguagePack = chalk.text.LanguagePack.English

  override def apply(sentence: String): String =
    sentence
      .split(" ")
      .map(languagePack.stemmer.getOrElse(identity[String] _))
      .mkString(" ")

}
