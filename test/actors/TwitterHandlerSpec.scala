//package actors
//
//import actors.TwitterHandler.{Fetch, FetchResponse}
//import akka.actor.ActorSystem
//import akka.testkit.{TestActorRef, TestKit}
//import modules.SparkUtil
//import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
//import twitter4j.conf.ConfigurationBuilder
//
//class TwitterHandlerSpec extends TestKit(ActorSystem("TwitterHandlerSpec"))
//                         with Matchers
//                         with WordSpecLike
//                         with BeforeAndAfterAll {
//
//  override def afterAll() { system.shutdown() }
//
//  def config = {
//
//    import com.typesafe.config.ConfigFactory
//
//    val myConfig = ConfigFactory.load("local.conf").getConfig("twitter")
//
//    val cb = new ConfigurationBuilder()
//    cb.setDebugEnabled(true)
//      .setOAuthConsumerKey(myConfig.getString("consumer.key"))
//      .setOAuthConsumerSecret(myConfig.getString("consumer.secret"))
//      .setOAuthAccessToken(myConfig.getString("access-token.key"))
//      .setOAuthAccessTokenSecret(myConfig.getString("access-token.secret"))
//      .setUseSSL(true)
//      .build()
//  }
//
//  "TwitterHandler" should {
//
//    "fetch tweets" in {
//
//      val real = TestActorRef[TwitterHandler](TwitterHandler.props(SparkUtil.sparkContext, config))
//      real ! Fetch("Apple")
//
//      expectMsgClass(FetchResponse.getClass)
//    }
//  }
//
//}
