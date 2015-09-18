package actors

import actors.TwitterHandler.{Fetch, FetchResult}
import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import modules.SparkUtil
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import twitter4j.conf.ConfigurationBuilder

class TwitterHandlerSpec extends TestKit(ActorSystem("TwitterHandlerSpec"))
                         with Matchers
                         with WordSpecLike
                         with BeforeAndAfterAll {

  override def afterAll() { system.shutdown() }

  def config = {

    import com.typesafe.config.ConfigFactory

    val myConfig = ConfigFactory.load("local.conf").getConfig("twitter")

    val cb = new ConfigurationBuilder()
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey(myConfig.getString("consumer.key"))
      .setOAuthConsumerSecret(myConfig.getString("my.config.prefix"))
      .setOAuthAccessToken(myConfig.getString("my.config.prefix"))
      .setOAuthAccessTokenSecret(myConfig.getString("my.config.prefix"))
      .build()
  }

  "TwitterHandler" should {

    "fetch tweets" in {

      val real = TestActorRef[TwitterHandler](TwitterHandler.props(SparkUtil.sparkContext, configuration))
      real ! Fetch("Apple")

      expectMsg(FetchResult("Apple", Seq("I like Apple")))
    }
  }

}
