package features

import play.api.Play.{configuration, current}

object Features {

  val coefficients = configuration.getInt("ml.features.coefficients").getOrElse(1500)

}