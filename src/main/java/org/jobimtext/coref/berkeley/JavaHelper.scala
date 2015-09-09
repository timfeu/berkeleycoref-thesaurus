package org.jobimtext.coref.berkeley

import scala.util.Random

object JavaHelper {
  def combineSequences[T] (left: Seq[T], right: Seq[T]): Seq[T] = left ++ right

  def drawXRandomElements[T] (seq: Seq[T], x: Int, seed: Long) = {
    val random = new Random(seed)
    random.shuffle(seq).take(x)
  }
}
