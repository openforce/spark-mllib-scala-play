package modules

import com.google.inject.AbstractModule
import org.apache.spark.SparkContext

class SparkModule extends AbstractModule {

  override def configure() = {

    bind(classOf[SparkContext]).toInstance(SparkUtil.sparkContext)

  }

}
