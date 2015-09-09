package edu.berkeley.nlp.coref

/**
 * Enumeration of methods to discretize a continuous value.
 *
 * @author Tim Feuerbach
 */
object DiscretizationMethod extends Enumeration {
  type DiscretizationMethod = Value

  /**
   * Simple threshold function. For example, if the value is an index and -1 means "not contained", the value will
   * be false for -1 and true otherwise.
   */
  val Boolean,

  /**
   * Uses same-sized finite intervals as a partition over the values.
   */
  Interval,

  /**
   * Uses precomputed intervals (e.g. chi-merge) from a Java properties file.
   */
  IntervalFile,

  /**
   * Uses the discretized probability.
   */
  Probability = Value

  def withNameNoReflection(name: String): Value = {
    name.toLowerCase match {
      case "boolean" => Boolean
      case "interval" => Interval
      case "intervalfile" => IntervalFile
      case "probability" => Probability
      case _ => throw new NoSuchElementException(s"Unknown DiscretizationMethod $name")
    }
  }
}
