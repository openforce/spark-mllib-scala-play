package modules

import org.apache.spark.{SparkConf, SparkContext}

object SparkUtil {

  val sparkContext = new SparkContext(new SparkConf().setMaster("local[*]").setAppName("spark-mllib-scala-play template"))

}
