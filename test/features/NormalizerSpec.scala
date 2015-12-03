package features

import org.scalatest.{MustMatchers, WordSpecLike}

class NormalizerSpec extends WordSpecLike with MustMatchers {

  "NoiseNormalizer" should {

    "shorten duplicate chars" in {
      NoiseNormalizer("cool and suuuuper cuuute") must equal("cool and suuper cuute")
    }

    "replace @username occurrences" in {
      NoiseNormalizer("@cmacher42 @Shokunin_San asked @hakla to change his username") must equal("username username asked username to change his username")
    }

    "replace urls" in {
      NoiseNormalizer("http://bit.ly/1NJl0Xu is short for http://www.openforce.com/") must equal("url is short for url")
    }

    "replace dash and lodash" in {
      NoiseNormalizer("lorem-ipsum dolor_sit amet") must equal("lorem ipsum dolor sit amet")
    }

  }

  "SentimentNormalizer" should {

    "replace emojis" in {
      SentimentNormalizer("i &lt;3 scala so much :)") must equal("i good scala so much good")
    }

    "replace unicode" in {
      SentimentNormalizer("i \u2764 scala so much \uD83D\uDE04") must equal("i good scala so much good")
    }

  }

  "ShortFormNormalizer" should {

    "replace short forms" in {
      ShortFormNormalizer("i didn't know that u haven't fixed that issue yet. haha. you're fired!") must equal("i did not know that you have not fixed that issue yet. ha. you are fired!")
    }

  }

}
