package twitter

import org.apache.spark.ml.UnaryTransformer
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.sql.types.{StringType, DataType}

class LinguisticTransformer(override val uid: String) extends UnaryTransformer[String, String, LinguisticTransformer] {

  def this() = this(Identifiable.randomUID("linguisticTransformer"))

  // TODO: normalize emoticons etc.
  override protected def createTransformFunc: String => String = transform

  override protected def validateInputType(inputType: DataType): Unit = require(inputType == StringType, s"Input type must be StringType but got $inputType.")

  override protected def outputDataType: DataType = StringType

  def transform(text: String): String = {
    var t = text.toLowerCase
    for ((emo, repl) <- emoRepl) t = t.replace(emo, repl)
    for ((regex, repl) <- reRepl) t = regex.replaceAllIn(t, repl)
    t.replace("-", " ").replace("_", " ")
  }

  private final val emoRepl = Map(
    // positive emoticons
    "&lt;3" -> " good ",
    " ->d" -> " good ",
    " ->dd" -> " good ",
    "8)" -> " good ",
    " ->-)" -> " good ",
    " ->)" -> " good ",
    ";)" -> " good ",
    "(- ->" -> " good ",
    "( ->" -> " good ",
    // negative emoticons
    " ->/" -> " bad ",
    " ->&gt;" -> " sad ",
    " ->')" -> " sad ",
    " ->-(" -> " bad ",
    " ->(" -> " bad ",
    " ->S" -> " bad ",
    " ->-S" -> " bad "
  )

  private final val reRepl = Map(
    "\br\b".r -> "you",
    "\bhaha\b".r -> "ha",
    "\bhahaha\b".r -> "ha",
    "\bdon't\b".r -> "do not",
    "\bdoesn't\b".r -> "does not",
    "\bdidn't\b".r -> "did not",
    "\bhasn't\b".r -> "has not",
    "\bhaven't\b".r -> "have not",
    "\bhadn't\b".r -> "had not",
    "\bwon't\b".r -> "will not",
    "\bwouldn't\b".r -> "would not",
    "\bcan't\b".r -> "can not",
    "\bcannot\b".r -> "can not"
  )

}