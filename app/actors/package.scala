import actors.TwitterHandler.FetchResponse
import org.apache.spark.rdd.RDD
import twitter.Tweet

package object actors {

  case object GetLatestModel

  case class Train(corpus: RDD[Tweet])

  case class GetFeatures(fetchResult: FetchResponse)

  case object Subscribe

  case object Unsubscribe

}
