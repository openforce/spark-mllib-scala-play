package actors

import actors.StatisticsServer.Corpus
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Play.{configuration, current}
import twitter.{TwitterHelper, Tweet}
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier._
import features.Transformers.default._

object CorpusInitializer {

  def props(sparkContext: SparkContext, batchTrainer: ActorRef, onlineTrainer: ActorRef, eventServer: ActorRef, statisticsServer: ActorRef) =
    Props(new CorpusInitializer(sparkContext, batchTrainer, onlineTrainer, eventServer, statisticsServer))

  case object InitFromStream

  case object LoadFromFs

  case object Finish

  val streamedTweetsSize = configuration.getInt("ml.corpus.initialization.tweets").getOrElse(500)

  val streamedCorpus = configuration.getBoolean("ml.corpus.initialization.streamed").getOrElse(true)

}

class CorpusInitializer(sparkContext: SparkContext, batchTrainer: ActorRef, onlineTrainer: ActorRef, eventServer: ActorRef, statisticsServer: ActorRef) extends Actor with ActorLogging {

  import CorpusInitializer._

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHelper.config))

  val csvFilePath = "data/testdata.manual.2009.06.14.csv"

  var posTweets: RDD[Tweet] = sparkContext.emptyRDD[Tweet]

  var negTweets: RDD[Tweet] = sparkContext.emptyRDD[Tweet]

  val totalStreamedTweetSize = streamedTweetsSize

  var stop = false


  override def postStop() = {
    ssc.stop(false)
  }

  override def preStart() = {
    if(streamedCorpus)
      self ! InitFromStream
    else
      self ! LoadFromFs
  }

  override def receive = LoggingReceive {

    case Finish =>
      log.debug("Received Finish message")
      log.info("Terminating streaming context...")
      ssc.stop(stopSparkContext = false, stopGracefully = true)
      val msg = s"Send ${posTweets.count} positive and ${negTweets.count} negative tweets to batch and online trainer"
      log.info(msg)
      eventServer ! msg
      val tweets: RDD[Tweet] = posTweets ++ negTweets
      tweets.cache()
      val trainMessage = Train(tweets)
      batchTrainer ! trainMessage
      onlineTrainer ! trainMessage
      context.stop(self)
      statisticsServer ! Corpus(tweets)
      eventServer ! "Corpus initialization finished"

    case LoadFromFs =>
      log.debug("Received LoadFromFs message")
      val msg = "Load tweets corpus from file system..."
      log.info(msg)
      eventServer ! msg

      def parseLabel(str: String): Option[Double] = str match {
        case "\"0\"" => Some(0)
        case "\"4\"" => Some(1)
        case x => None
      }

      val data = sparkContext.textFile(csvFilePath)
        .map(line => line.split(",").map(elem => elem.trim))
        .flatMap(ar => parseLabel(ar(0)).map(label => Tweet(text = ar(5), sentiment = label)))

      posTweets = data.filter(t => t.sentiment == 1)
      negTweets = data.filter(t => t.sentiment == 0)

      self ! Finish


    case InitFromStream =>
      log.debug("Received InitFromStream message")
      val msg = "Initialize tweets corpus from twitter stream..."
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
