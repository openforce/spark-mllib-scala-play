package modules

import com.google.inject.AbstractModule
import org.apache.spark.SparkContext
import play.api.Logger

class SparkModule extends AbstractModule {

  val logger = Logger(this.getClass)

  override def configure() = {

    bind(classOf[SparkContext]).toInstance(SparkUtil.sparkContext)

  }

}
