package org.jobimtext.coref.berkeley

import edu.berkeley.nlp.futile.util.Logger

import scala.collection.mutable
import org.jobimtext.api.struct.Sense

/**
 * Structure to cache various expansions obtained from a distributional thesaurus. Each thesaurus must have its own
 * cache. All cache methods allow to specify the calculation of the corresponding value, which is used if it isn't
 * stored in the cache. The calculation is deferred (like in `Map.getOrElse()`). The methods' currying allow you to
 * write natural looking syntax, e.g.:
 *
 * {{{
 *   cache.priorTermExpansionCache("president") {
 *     database.termPriorExpansion("president")
 *   }
 * }}}
 *
 * It is recommended to empty the cache by calling `clearCache()` on a regular basis, for example after each
 * document processed.
 *
 * @param enabled If false, the `fallback` of a cache method will always be called without caching the result.
 *
 * @author Tim Feuerbach
 */
class ThesaurusCache(enabled: Boolean = true) {
  type Term = String
  type DocumentId = String

  protected var _priorTermExpansionCache = scala.collection.mutable.Map.empty[Term,
    scala.collection.mutable.LinkedHashMap[String, ExpansionIndexHolder]]
  protected var _rerankedExpansionCache = scala.collection.mutable.Map.empty[(Term, Set[String]),
    scala.collection.mutable.LinkedHashMap[Term, ExpansionIndexHolder]]
  protected var _sensesExpansionCache = mutable.Map.empty[Term, Array[Sense]]
  protected var _contextExpansionCache = mutable.Map.empty[Set[String], mutable.LinkedHashMap[String,
    ExpansionIndexHolder]]

  /*
  Non-expansions
   */
  protected var _termCountLogCache = mutable.Map.empty[String, Double]

  protected def cacheElement[T, K](key: K, cache: mutable.Map[K, T])(fallback: => T): T = {
    if (!enabled) {
      fallback
    } else {
      cache.getOrElseUpdate(key, fallback)
    }
  }

  /**
   * Returns the cached prior expansion of a term. If the expansion is not in the cache, it will be computed
   * from the fallback and stored in the cache.
   *
   * @param term the term to expand
   * @param fallback calculation of the expansion, used if the element is not in the cache
   *
   * @return cached term's cached prior expansion or the result of the fallback
   */
  def priorTermExpansionCache(term: String)(fallback: => scala.collection.mutable.LinkedHashMap[String,
    ExpansionIndexHolder]) = {
    cacheElement(term, _priorTermExpansionCache)(fallback)
  }

  /**
   * Returns the cached re-ranked expansion of a term while taking the given context features into account. If the
   * expansion is not in the cache, it will be computed from the fallback and stored in the cache.
   *
   * @param term the term to expand
   * @param context the context features to consider
   * @param fallback calculation of the expansion, used if the element is not in the cache
   *
   * @return cached term's re-ranked expansion or the result of the fallback
   */
  def rerankedExpansionCache(term: String, context: Set[String])(fallback: => scala.collection.mutable
  .LinkedHashMap[String,
    ExpansionIndexHolder]) = {
    cacheElement((term, context), _rerankedExpansionCache)(fallback)
  }

  /**
   * Returns a the term's cached set of sense clusters. If the elements are not members of the cache, they will be
   * computed from the fallback and stored in the cache.
   *
   * @param term the term to retrieve the sense clusters for
   * @param fallback calculation of the clusters, used if the element is not in the cache
   *
   * @return cached sense clusters of the term or the result of the fallback
   */
  def sensesCache(term: String)(fallback: => Array[Sense]) = {
    cacheElement(term, _sensesExpansionCache)(fallback)
  }

  /**
   * Returns the cached context-based expansion for the given set of features.  If the expansion is not in the cache,
   * it will be computed from the fallback and stored in the cache.
   *
   * @param context the context features for the C-expansion
   * @param fallback  calculation of the expansion, used if the element is not in the cache
   *
   * @return cached context expansion or the result of the fallback
   */
  def contextExpansionCache(context: Set[String])(fallback: => mutable.LinkedHashMap[String,
    ExpansionIndexHolder]): mutable.LinkedHashMap[String, ExpansionIndexHolder] = {
    cacheElement(context, _contextExpansionCache)(fallback)
  }

  /**
   * Returns the cached count of one or more terms found in the given feature as its ''natural logarithm''.  If the
   * value is not in the cache, it will be computed from the fallback and stored in the cache.
   *
   * @param feature a context feature containing one or more terms
   * @param fallback calculation of the ''natural logarithm'' of the term count
   *
   * @return cached count log or the result of the fallback
   */
  def termCountLogCache(feature: String)(fallback: => Double): Double = {
    cacheElement(feature, _termCountLogCache)(fallback)
  }

  /**
   * Clears the cache. Should be called on a regular basis to save memory.
   */
  def clearCache(): Unit = {
    _priorTermExpansionCache = scala.collection.mutable.Map.empty[Term,
      scala.collection.mutable.LinkedHashMap[String, ExpansionIndexHolder]]
    _sensesExpansionCache = mutable.Map.empty[Term, Array[Sense]]
    _contextExpansionCache = mutable.Map.empty[Set[String], mutable.LinkedHashMap[String, ExpansionIndexHolder]]
    _rerankedExpansionCache = scala.collection.mutable.Map.empty[(Term, Set[String]),
      scala.collection.mutable.LinkedHashMap[Term, ExpansionIndexHolder]]

    _termCountLogCache = mutable.Map.empty[String, Double]

    //Logger.logs("Cache cleared")
  }
}

case class ExpansionIndexHolder(index: Int, score: Double)