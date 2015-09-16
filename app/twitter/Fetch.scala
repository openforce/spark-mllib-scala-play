package twitter

import java.io.File
import scala.io.Source

object Fetch {

  def run  = {

    val dataPath = "data"
    val inList = Source.fromFile(s"$dataPath/corpus.csv")
    val outList = new File(s"$dataPath/full-corpus.csv")
    val rawDir = new File(s"$dataPath/rawdata")
    inList.getLines().foreach(print)

  }

}
