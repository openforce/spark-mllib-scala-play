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
import util.SentimentIdentifier

object CorpusInitializer {

  def props(sparkContext: SparkContext, trainer: ActorRef) = Props(new CorpusInitializer(sparkContext, trainer))

  case object Init

  case object Finish
}

class CorpusInitializer(sparkContext: SparkContext, trainer: ActorRef) extends Actor {

  val log = Logger(this.getClass)

  val ssc = new StreamingContext(sparkContext, Duration(1000))

  val twitterAuth = Some(new OAuthAuthorization(TwitterHandler.config))

  var counter = 0L

  var tweets: Seq[Tweet] = Nil

  val maxTweets = 500

  var stop = false

  self ! Init

  override def receive = {

    case Finish => {
      log.info(s"Terminating stream...")
      ssc.stop(false, true)
      trainer ! Train(tweets)
      context.stop(self)
    }

    case Init => {

      log.info(s"Initialize tweets corpus")

      val stream = TwitterUtils.createStream(ssc, twitterAuth, filters = SentimentIdentifier.sentimentEmoticons)
        .filter(t => t.getUser.getLang == "en" && !t.isRetweet)
        .map(Tweet(_))

      stream.foreachRDD { rdd =>
        val newTweets = rdd.collect()
        if(!newTweets.isEmpty) {
          println(s"*** Collected tweets $counter of $maxTweets")
          println(newTweets.foreach(println))
        }
        tweets = tweets ++ newTweets
        counter = counter + rdd.count()

        if(counter >= maxTweets && !stop) {
          stop = true
          self ! Finish
        }
      }

      ssc.start()
    }
  }
}
