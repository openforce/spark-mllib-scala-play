package twitter

import org.apache.spark.SparkContext
import play.api.Logger
import twitter4j.conf.Configuration
import twitter4j.{Query, TwitterFactory}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object TwitterHelper {

  val numTweetsToCollect = 10

  val log = Logger(this.getClass)

  def fetch(keyword: String, sparkContext: SparkContext, twitterConfig: Configuration): Seq[String] = {
    log.info(s"Start fetching tweets filtered by keyword=$keyword")

    val twitterFactory = new TwitterFactory(twitterConfig)
    val twitter = twitterFactory.getInstance()
    val query = new Query(s"$keyword -filter:retweets").lang("en")
    val result = twitter.search(query)
    result.getTweets.take(1000).map(_.getText()).toList
  }
}
