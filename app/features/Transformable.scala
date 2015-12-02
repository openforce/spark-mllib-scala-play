package features

import chalk.text.LanguagePack

trait Transformable extends Function1[String, String] with Serializable

object EnglishStemmer extends Transformable {

  implicit val languagePack: LanguagePack = chalk.text.LanguagePack.English

  override def apply(sentence: String): String = {
    sentence
      .split(" ")
      .map(languagePack.stemmer.getOrElse(identity[String] _))
      .mkString(" ")
  }

}
