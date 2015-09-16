package twitter

import org.scalatest._

class FetchSpec extends FlatSpec with Matchers {

  "Fetch " should "run" in {

    Fetch.run

    true should be(true)
  }

}
