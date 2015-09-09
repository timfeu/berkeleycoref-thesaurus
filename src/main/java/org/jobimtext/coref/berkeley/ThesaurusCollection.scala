package org.jobimtext.coref.berkeley

import org.jobimtext.api.db.Destroyable

/**
 * Collection of all [[DistributionalThesaurusComputer]]s used during the current run, including the features to use.
 *
 * @param thesauri Mapping from distributional thesaurus identifiers to the thesauri themselves. It holds for every pair
 *                 that <code>key == map(key).identifier</code>.
 * @param featuresToUse features to use during this run
 * @param connectedInterfaces all database interfaces connected to
 *
 * @author Tim Feuerbach
 */
class ThesaurusCollection(val thesauri: Map[String, DistributionalThesaurusComputer],
                          val featuresToUse: Array[ThesaurusFeature],
                           val connectedInterfaces: Seq[Destroyable]) {

  /**
   * Returns the thesaurus identified by the given identifier.
   *
   * @param identifier The identifier provided to the thesaurus object at creation time.
   *
   * @return Some[DistributionalThesaurus] if a thesaurus with this identifier is stored in the collection, None els
   */
  def getByKey(identifier: String) = thesauri.getOrElse(identifier, throw new IndexOutOfBoundsException("Unknown thesaurus identifier"))

  /**
   * Returns all the thesauri in this collection in a random order.
   *
   * @return all thesauri in this collection
   */
  def all = thesauri.values

  /**
   * Closes all connections. All thesauri of this collection become unusable afterwards.
   */
  def closeConnections(): Unit = {
    connectedInterfaces.foreach(_.destroy())
  }
}