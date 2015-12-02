package features

import org.scalatest.{MustMatchers, WordSpecLike}

class TokenizerSpec extends WordSpecLike with MustMatchers {

  "Tokenizer.unigram" should {

    "create unigrams" in {
      val text = "i love my kindle2. the 2 is fantastic."
      Tokenizer.unigram(text) must equal(Seq("i", "love", "my", "kindle2", "the", "2", "is", "fantastic"))
    }

  }

  "Tokenizer.bigram" should {

    "create bigrams for each sentence" in {
      val text = "i love my kindle2. the 2 is fantastic."
      Tokenizer.bigram(text) must equal(Seq("i love", "love my", "my kindle2", "the 2", "2 is", "is fantastic"))
    }

  }

  "Tokenizer.trigram" should {

    "create trigrams for each sentence" in {
      val text = "i love my kindle2. the 2 is fantastic."
      Tokenizer.trigram(text) must equal(Seq("i love my", "love my kindle2", "the 2 is", "2 is fantastic"))
    }

  }

  "Tokenizer.ngram" should {

    "create unigrams" in {
      val text = "i love my kindle2. the 2 is fantastic."
      Tokenizer.ngram(1)(text) must equal(Seq("i", "love", "my", "kindle2", "the", "2", "is", "fantastic"))
    }

  }

}
