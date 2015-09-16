package util

import scala.io.{Source, BufferedSource}

object SourceUtil {

  def readSourceOpt[T](name: String)(handler: Option[BufferedSource] => T): T = {
    val maybeBufferedSource = try {
      Some(Source.fromFile(name))
    } catch {
      case e: Exception => None
    }
    maybeBufferedSource.map { bs =>
      try {
        handler(Some(bs))
      } finally {
        bs.close()
      }
    } getOrElse { handler(None)}
  }

}
