package org.jobimtext.coref.berkeley

/**
 * Allows the creation of Function0 objects from Java code.
 */
abstract class Function0Helper[T] extends Function0[T] {
  def applyImpl(): T
  override def apply(): T = applyImpl()
}
