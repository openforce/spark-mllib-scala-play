package features

import org.scalatest.{MustMatchers, WordSpecLike}

class TokenizerSpec extends WordSpecLike with MustMatchers {

  "UnigramTokenizer" should {

    "create unigrams" in {
      val text = "i love my kindle2. the 2 is fantastic."
      UnigramTokenizer(text) must equal(Seq("i", "love", "my", "kindle2", "the", "2", "is", "fantastic"))
    }

  }

  "BigramTokenizer" should {

    "create bigrams for each sentence" in {
      val text = "i love my kindle2. the 2 is fantastic."
      BigramTokenizer(text) must equal(Seq("i love", "love my", "my kindle2", "the 2", "2 is", "is fantastic"))
    }

  }

}
