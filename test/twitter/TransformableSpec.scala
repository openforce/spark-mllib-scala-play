package twitter

import cats.syntax.functor._
import cats._, cats.std.all._
import features.{NoiseNormalizer, SentimentNormalizer}
import org.scalatest.{MustMatchers, WordSpecLike}

class TransformableSpec extends WordSpecLike with MustMatchers{

  "Transformers" should {

    "compose" in {

      Tweet("foobar :) @openforce").tokens must equal(Seq("foobar", "good", "usernam", "foobar good", "good usernam"))

    }

  }

}
