package twitter

import org.apache.spark.SparkContext
import play.api.Logger
import twitter4j.conf.Configuration
import twitter4j.{Query, TwitterFactory}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object Collect {

  val numTweetsToCollect = 10

  val log = Logger(this.getClass)

  def fetch(token: String, sparkContext: SparkContext, twitterConfig: Configuration): Seq[String] = {
      log.info(s"Start fetching tweets for token=$token")

    val collectedTweets = new ListBuffer[String]()
    val twitterFactory = new TwitterFactory(twitterConfig)
    val twitter = twitterFactory.getInstance()
    val query = new Query(s"$token")
    val result = twitter.search(query)
    result.getTweets.take(100).map(status => collectedTweets.append(status.getText()))
    collectedTweets
  }
}
