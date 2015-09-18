package twitter

import org.apache.spark.SparkContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.Configuration

import scala.collection.mutable.ListBuffer

object Collect {

  val numTweetsToCollect = 10

  val log = Logger(this.getClass)

  def fetch(token: String, sparkContext: SparkContext, twitterConfig: Configuration): Seq[String] = {
      log.info(s"Start fetching tweets for token=$token")

      val ssc = new StreamingContext(sparkContext, Duration(1000))

      val twitterAuth = Some(new OAuthAuthorization(twitterConfig))
      val tweets = TwitterUtils.createStream(ssc, twitterAuth, filters = Seq(token)).map(_.getText)

      val collectedTweets = new ListBuffer[String]()
      tweets.foreachRDD((rdd, time) => {
        val count = rdd.count()
        log.debug(s"=================>>>>> Count=$count, RDD=$rdd")
        if (count > 0) {
          collectedTweets.appendAll(rdd.collect())
        log.debug(s"=================>>>>> CollectedTweets=$collectedTweets")
          if (collectedTweets.length >= numTweetsToCollect) {
            ssc.stop()
          }
        }
      })

      ssc.start()
      ssc.awaitTermination()

      collectedTweets.toList
  }
}
