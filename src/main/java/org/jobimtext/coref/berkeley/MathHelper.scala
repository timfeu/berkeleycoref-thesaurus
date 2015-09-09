package org.jobimtext.coref.berkeley

object MathHelper {
  private val logOf2 = Math.log(2.0)

  def log2(d: Double) = Math.log(d) / logOf2
}
