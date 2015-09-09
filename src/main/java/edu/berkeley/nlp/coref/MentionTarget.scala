package edu.berkeley.nlp.coref

/**
 * @author Tim Feuerbach
 */
object MentionTarget extends Enumeration {
  type MentionTarget = Value
  val Current, Antecedent = Value

  def withNameNoReflection(name: String): Value = {
    name.toLowerCase match {
      case "current" => Current
      case "antecedent" => Antecedent
      case _ => throw new NoSuchElementException(s"Unknown MentionTarget $name")
    }
  }
}
