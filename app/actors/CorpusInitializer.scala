package actors

import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorRef, Props}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import play.api.Play.{configuration, current}
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier._

object CorpusInitializer {

  def props(sparkContext: SparkContext, trainer: ActorRef, eventServer: ActorRef) = Props(new CorpusInitializer(sparkContext, trainer, eventServer))

  case object Init

  case object Load

  case object Finish

  val streamedTweetsSize = configuration.getInt("ml.corpus.streamed-tweets-size").getOrElse(500)

}

class CorpusInitializer(sparkContext: SparkContext, trainer: ActorRef, eventServer: ActorRef) extends Actor {

  import CorpusInitializer._

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  val csvFilePath = "data/trainingandtestdata/testdata.manual.2009.06.14.csv"

  var posTweets: RDD[Tweet] = sparkContext.emptyRDD[Tweet]
  var negTweets: RDD[Tweet] = sparkContext.emptyRDD[Tweet]

  val totalStreamedTweetSize = streamedTweetsSize

  var stop = false

  override def preStart() = {
    if(Files.exists(Paths.get(csvFilePath)))
      self ! Load
    else
      self ! Init
  }

  override def receive = {

    case Finish => {
      log.info(s"Terminating streaming context...")
      ssc.stop(false, true)
      val msg = s"Send ${posTweets.count} positive and ${negTweets.count} negative tweets to trainer"
      log.info(msg)
      eventServer ! msg
      trainer ! Train(posTweets ++ negTweets)
      context.stop(self)
    }

    case Load => {
      val msg = s"Load tweets corpus from file system..."
      log.info(msg)
      eventServer ! msg

      def parseLabel(str: String): Option[Double] = str match {
        case "\"0\"" => Some(0)
        case "\"4\"" => Some(1)
        case x => None
      }

      val data = sparkContext.textFile(csvFilePath)
        .map(line => line.split(",").map(elem => elem.trim))
        .flatMap(ar => parseLabel(ar(0)).map(label => Tweet(tweetText = ar(5), label = label)))

      posTweets = data.filter(t => t.sentiment == 1)
      negTweets = data.filter(t => t.sentiment == 0)

      self ! Finish
    }

    case Init => {

      val msg = s"Initialize tweets corpus from twitter stream..."
      log.info(msg)
      eventServer ! msg

      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map(Tweet(_))

      stream.foreachRDD { rdd =>

        posTweets = add(posTweets, rdd.filter(t => t.sentiment == 1).collect())
        negTweets = add(negTweets, rdd.filter(t => t.sentiment == 0).collect())

        if(stopCollection) {
          stop = true
          self ! Finish
        } else {
          val msg = s"Collected ${posTweets.count} positive tweets and ${negTweets.count} negative tweets of total $totalStreamedTweetSize"
          println(s"*** $msg")
          eventServer ! msg
        }
      }

      ssc.start()

      def add(rdd: RDD[Tweet], tweets: Array[Tweet]) = if(!reachedMax(rdd)) rdd ++ sparkContext.makeRDD(tweets) else rdd

      def reachedMax(s: RDD[Tweet]) = s.count >= totalStreamedTweetSize / 2
      def stopCollection = reachedMax(posTweets) && reachedMax(negTweets) && !stop
    }
  }


}
