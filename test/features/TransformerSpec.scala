package features

import org.scalatest.{MustMatchers, WordSpecLike}

class TransformerSpec extends WordSpecLike with MustMatchers{

  "DefaultTransformer" should {

    "normalize, stem and tokenize a string" in {

      features.Transformers.default.DefaultTransformer.transform("foobar :) @openforce") must equal(Seq("foobar", "good", "usernam", "foobar good", "good usernam"))

    }

  }

  "Custom transformers" should {

    "be composable" in {

      val customNormalizer = new Normalizable[String] {

        override val mapping: Map[String, String] = Map("openforce" -> "typesafe")

        override def transformFn(sentence: String): (String, String) => String = (a, b) => sentence.replace(a, b)

      }

      val customTokenizer = new Tokenizer {

        override def apply(sentence: String): Seq[String] = sentence.split(" ")

      }

      val transformer = customNormalizer andThen customTokenizer

      transformer("hello openforce universe") must equal(Seq("hello", "typesafe", "universe"))

    }

  }

}
