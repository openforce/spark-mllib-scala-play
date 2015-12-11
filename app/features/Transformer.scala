package features

trait Transformer {

  def transform: String => Seq[String]

}

object Transformers {

  object default {

    implicit object DefaultTransformer extends Transformer {

      override def transform: String => Seq[String] =
        SentimentNormalizer andThen
        ShortFormNormalizer andThen
        NoiseNormalizer andThen
        EnglishStemmer andThen
        { (sentence: String) => Tokenizer.unigram(sentence) ++ Tokenizer.bigram(sentence) }

    }

  }

}