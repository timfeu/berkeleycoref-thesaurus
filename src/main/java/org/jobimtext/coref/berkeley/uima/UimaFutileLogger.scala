package org.jobimtext.coref.berkeley.uima

import edu.berkeley.nlp.futile.util.Logger
import org.apache.uima.util.Level

/**
 * Directs logging messages to the Uima logger.
 */
class UimaFutileLogger(val logger: org.apache.uima.util.Logger) extends Logger.LogInterface {
  override def logsf(format: String, elements: AnyRef*): Unit = {
    logger.log(Level.FINE, format.format(elements:_*))
  }

  override def startTrack(p1: scala.Any): Unit = {}

  override def startTrack(p1: scala.Any, p2: Boolean): Unit = {}

  override def warnf(format: String, elements: AnyRef*): Unit = {
    logger.log(Level.WARNING, format.format(elements:_*))
  }

  override def errf(format: String, elements: AnyRef*): Unit = {
    logger.log(Level.SEVERE, format.format(elements:_*))
  }

  override def warn(message: scala.Any): Unit = {
    logger.log(Level.WARNING, message.toString)
  }

  override def logs(message: scala.Any): Unit = {
    logger.log(Level.FINE, message.toString)
  }

  override def logssf(format: String, elements: AnyRef*): Unit = {
    logger.log(Level.FINER, format.format(elements:_*))
  }

  override def logss(message: scala.Any): Unit = {
    logger.log(Level.FINER, message.toString)
  }

  override def dbg(message: scala.Any): Unit = logger.log(Level.FINEST, message.toString)

  override def err(message: scala.Any): Unit = logger.log(Level.SEVERE, message.toString)

  override def endTrack(): Unit = {}
}
