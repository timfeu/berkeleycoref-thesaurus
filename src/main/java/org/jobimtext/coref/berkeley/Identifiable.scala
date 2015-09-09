package org.jobimtext.coref.berkeley

/**
 * Interface for objects that hold an unique identifier. The uniqueness is defined as follows: Given a class C
 * implementing `Identifiable` while no ancestor of C implements it, it holds that if o and o' are instances of C:
 * o.identifier == o'.identifier => o == o'
 *
 * @author Tim Feuerbach
 */
trait Identifiable {
  def identifier: String
}
