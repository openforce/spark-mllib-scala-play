package actors

import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.BeforeAndAfterEach

trait SparkTestContext {
 self: BeforeAndAfterEach  =>

  var sc: SparkContext = _

  override protected def beforeEach() = {
    sc = createSparkContext()
  }

  override protected def afterEach() = {
    sc.stop()
  }

  def createSparkContext(): SparkContext = {
    val conf = new SparkConf().setAppName("test").setMaster("local")
      .set("spark.driver.allowMultipleContexts", "true")
    val sc = new SparkContext(conf)
    sc
  }

}
