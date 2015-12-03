package features

import org.scalatest.{MustMatchers, WordSpecLike}

class StemmerSpec extends WordSpecLike with MustMatchers {

   "Stemmer" should {

     "stem english words" in {
       EnglishStemmer("the boy cars are different colors") must equal("the boi car ar differ color")
     }

   }

 }
