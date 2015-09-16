package twitter

import org.apache.spark.ml.UnaryTransformer
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.sql.types.{StringType, DataType}

class LinguisticTransformer(override val uid: String) extends UnaryTransformer[String, String, LinguisticTransformer] {

  def this() = this(Identifiable.randomUID("linguisticTransformer"))

  // TODO: normalize emoticons etc.
  override protected def createTransformFunc: String => String = text => text.toLowerCase.replaceAll("[-_]", " ")

  override protected def validateInputType(inputType: DataType): Unit = require(inputType == StringType, s"Input type must be StringType but got $inputType.")

  override protected def outputDataType: DataType = StringType

}