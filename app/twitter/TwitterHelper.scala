package twitter

import org.apache.spark.SparkContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.Configuration
import twitter4j.{Query, TwitterFactory}

import scala.collection.JavaConversions._

object TwitterHelper {

  val numTweetsToCollect = 10

  val log = Logger(this.getClass)

  def fetch(keyword: String, sparkContext: SparkContext, twitterConfig: Configuration): Seq[String] = {
    log.info(s"Start fetching tweets filtered by keyword=$keyword")

    val twitterFactory = new TwitterFactory(twitterConfig)
    val twitter = twitterFactory.getInstance()
    val query = new Query(s"$keyword -filter:retweets").lang("en")
    val result = twitter.search(query)
    result.getTweets.take(100).map(_.getText()).toList
  }

  def stream(token: String, sparkContext: SparkContext, twitterConfig: Configuration): DStream[String] = {
    log.info(s"Start fetching tweets for token=$token")

    val ssc = new StreamingContext(sparkContext, Duration(1000))

    val twitterAuth = Some(new OAuthAuthorization(twitterConfig))
    val tweets = TwitterUtils.createStream(ssc, twitterAuth, filters = Seq(token)).map(_.getText)

    ssc.start()
    ssc.awaitTermination()
    tweets
  }
}
