package actors

import actors.CorpusInitializer.{Finish, Init}
import actors.OnlineTrainer.Train
import akka.actor.{Actor, ActorRef, Props}
import org.apache.spark.SparkContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Duration, StreamingContext}
import play.api.Logger
import twitter.Tweet
import twitter4j.auth.OAuthAuthorization
import util.SentimentIdentifier._

object CorpusInitializer {

  def props(sparkContext: SparkContext, trainer: ActorRef, eventServer: ActorRef) = Props(new CorpusInitializer(sparkContext, trainer, eventServer))

  case object Init

  case object Finish
}

class CorpusInitializer(sparkContext: SparkContext, trainer: ActorRef, eventServer: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  var posTweets: Seq[Tweet] = Nil
  var negTweets: Seq[Tweet] = Nil

  val totalTweetSize = 500

  var stop = false

  var feedbackListeners = Seq.empty[ActorRef]

  self ! Init

  override def receive = {

    case Finish => {
      log.info(s"Terminating stream...")
      ssc.stop(false, true)
      trainer ! Train(posTweets ++ negTweets)
      context.stop(self)
    }

    case Init => {

      log.info(s"Initialize tweets corpus")

      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map(Tweet(_))

      stream.foreachRDD { rdd =>
        val newTweets = rdd.collect()
        val (pos, neg) = newTweets.partition(isPositive)

        if(!reachedMax(posTweets)) posTweets = posTweets ++ pos
        if(!reachedMax(negTweets)) negTweets = negTweets ++ neg

        if(reachedMax(posTweets) && reachedMax(negTweets) && !stop) {
          stop = true
          self ! Finish
        } else {
          val msg = s"Collected ${posTweets.size} positive tweets and ${negTweets.size} negative tweets of total $totalTweetSize"
          println(s"*** $msg")
          println(newTweets.foreach(println))
          eventServer ! msg
        }
      }

      ssc.start()
    }
  }

  def reachedMax(s: Seq[Tweet]) = s.size >= totalTweetSize / 2
}
