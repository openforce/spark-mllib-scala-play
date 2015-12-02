package twitter

import features.Transformable
import org.scalatest.{MustMatchers, WordSpecLike}

class TransformableSpec extends WordSpecLike with MustMatchers{

  "A transformable" should {

    "replace emoji" in {
      Transformable.transformSentence(" I feel \uD83D\uDE15") === "I feel  bad "
      Transformable.transformSentence(" I feel \uD83D\uDE1F") === "I feel  bad "
      Transformable.transformSentence(" I feel \uD83D\uDE1B") === "I feel  good "
    }
  }

  "Transformables" should {

    "be composable" in {
      val pipeline: Transformable = SentimentTransformable compose NoiseTranformable

      val tweet = new Tweet(pipeline.map()) extends BigramTokenizer
    }

  }

}
