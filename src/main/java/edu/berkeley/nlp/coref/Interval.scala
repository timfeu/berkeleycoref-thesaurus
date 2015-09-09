package edu.berkeley.nlp.coref

/**
 * @author Tim Feuerbach
 */
@SerialVersionUID(1L)
trait Interval extends Serializable {
  def contains(x: Int): Boolean
}

@SerialVersionUID(1L)
class RangeInterval(val start: Int, val end: Int) extends Interval {
  require(start != end, "Interval start and end may not have the same values, this would result in a 0 size interval")

  override def contains(x: Int): Boolean = x >= start && x < end

  override def toString: String = "[" + start + "," + end + ")"
}

object OpenInterval {
  object OpenSide extends Enumeration {
    type OpenSide = Value
    val Left, Right = Value
  }
}
@SerialVersionUID(1L)
class OpenInterval(val anchor: Int, openSide: OpenInterval.OpenSide.OpenSide) extends Interval {
  override def contains(x: Int): Boolean = if (openSide == OpenInterval.OpenSide.Left) x <= anchor else x >= anchor

  override def toString: String = if (openSide == OpenInterval.OpenSide.Left) "(-∞," + anchor + "]" else "[" + anchor + ",∞)"
}