package org.jobimtext.coref.berkeley

import edu.berkeley.nlp.coref.config.CorefSystemConfiguration
import edu.berkeley.nlp.coref.{BaseDoc, Mention}
import edu.berkeley.nlp.math.LogAdder
import org.jobimtext.api.db.AntonymDatabase
import org.jobimtext.api.struct.{IThesaurusDatastructure, Sense}
import org.jobimtext.coref.berkeley.DistributionalThesaurusComputer.AttributeIncompatibilityResult

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Common interface for mention or mention pair properties that are computed by looking up terms or context features
 * in a distributional thesaurus.
 *
 * The trait has to be implemented by the holing system. Users of this interface don't kneed to know how the holing is
 * accomplished, given that the [[edu.berkeley.nlp.coref.Mention]] provides enough context to get that information.
 *
 * The default implementation of context-based expansion makes the assumption that each context feature is conditionally
 * independent of all others. If that is not the case, you have to rewrite the expansion construction by overriding
 * `contextExpansion`.
 *
 * The computer is used in two places: First, like the [[edu.berkeley.nlp.coref.NumberGenderComputer]], it is consulted
 * to precompute the terms and context features of each mention when the document is being loaded. Second, it is used
 * by the featurizer to compute the individual feature values of mentions or mention pairs.
 *
 * @author Tim Feuerbach
 */
trait DistributionalThesaurusComputer {
  /**
   * A name for the underlying thesaurus. Used by [[edu.berkeley.nlp.coref.PairwiseIndexingFeaturizerJoint]] to
   * distinguish the feature values coming from different thesauri.
   */
  val identifier: String

  /**
   * Interface to the actual thesaurus database, connected.
   */
  protected val interface: IThesaurusDatastructure[String, String]

  var antonymDatabase: Option[AntonymDatabase] = None

  /**
   * Maximum number of terms in a prior expansion returned by the underlying thesaurus implementation. Is also
   * used to limit the context-based expansion.
   */
  var maxPriorExpansionSize: Int = 200

  protected val config: CorefSystemConfiguration

  protected val cache: ThesaurusCache

  /**
   * Contains the context features of a mention that are not part of the mention itself.
   */
  protected var outerMentionContextFeaturesCache = mutable.Map.empty[Mention, Set[String]]

  def clearCache() = {
    outerMentionContextFeaturesCache = mutable.Map.empty[Mention, Set[String]]
    cache.clearCache()
  }

  // STATISTICS
  var priorTermExpansionFindings = 0
  var priorTermExpansionTrials = 0
  val priorTermExpansionFindingsFrequency: ArrayBuffer[String] = if (config.dtStatistics) ArrayBuffer.empty[String]
  else null
  var attributeTermExpansionFindings = 0
  var attributeTermExpansionTrials = 0
  val contextScores: mutable.Map[String, Double] = if (config.dtStatistics) mutable.Map.empty[String, Double] else null

  /**
   * Creates the term representation of the given mention. Usually the mention's head word will be used. This method
   * is called during the initial document setup phase to store the term in the mention's cache.
   *
   * The default implementation calls `getTerm(sentIdx, headIdx, mention.rawDoc)`.
   *
   * @param mention the mention to convert to a term
   * @return the string representation of the mention's term
   */
  def extractTerm(mention: Mention): String = getTerm(mention.sentIdx, mention.headIdx, mention.rawDoc)

  /**
   * Extracts the context features of the given mention.  This method is called during the initial document setup
   * phase to store the context in the mention's cache.
   *
   * @param mention the mention to extract the context features
   * @return a collection of context features of the given mention
   */
  def extractContext(mention: Mention): Set[String]

  /**
   * Returns the mention's term in the context of this thesaurus. Retrieves the previously extracted term from the
   * mention's cache.
   *
   * @param mention the mention
   *
   * @return the mention's term representation according to this thesaurus
   *
   * @throws IllegalStateException if the mention has no precomputed context features for this thesaurus stored
   */
  @throws[IllegalStateException]("if the term has not been precomputed")
  def getTerm(mention: Mention) = {
    mention.termCache.getOrElse(identifier, throw new IllegalStateException("Term for mention " + mention + " and " +
      "thesaurus " + identifier + " not precomputed"))
  }

  /**
   * Converts a single word to its term representation. If the thesaurus does not operate on terms, this
   * method should throw an [[UnsupportedOperationException]]. In that case, `extractTerm` has to be overridden, since
   * its default implementation depends on this method.
   *
   * @param sentIdx the sentence index relative to the document
   * @param wordIdx word index relative to the sentence
   * @param rawDoc the document containing this particular word
   *
   */
  @throws[UnsupportedOperationException]("if this thesaurus' holing operation does not operate on single words")
  def getTerm(sentIdx: Int, wordIdx: Int, rawDoc: BaseDoc): String

  /**
   * Returns the mentions' set of context features in the context of this thesaurus. Retrieves the previously
   * extracted features from the mention's cache.
   *
   * @param mention the mention
   *
   * @return the mention's context features according to this thesaurus
   *
   * @throws IllegalStateException if the mention has no precomputed context features for this thesaurus stored
   */
  def getContext(mention: Mention) = {
    mention.bimCache.getOrElse(identifier, throw new IllegalStateException("Context features for mention " + mention
      + " and thesaurus " + identifier + " not precomputed"))
  }

  /**
   * Looks up the first mention's term in the second mention's term's prior expansion and reports its rank. If the
   * term is not contained at all, -1 is returned, and -2 if the second term's expansion is empty. 0 is returned
   * if the two terms are identical strings.
   *
   * @param first the first mention, whose term representation is used as a search key
   * @param second the second mention, whose term will be expanded
   *
   * @return 0 if the two terms are identical; -2 if the second term has no expansion; -1 if the first term is not
   *         contained in the second term's expansion at all. Otherwise, the first term's position in the second
   *         term's expansion in natural ordering, meaning that 1 ist the first element in the expansion list.
   */
  def positionInPriorExpansion(first: Mention, second: Mention): Int = positionInPriorExpansion(getTerm(first),
    getTerm(second))

  /**
   * Looks up the first mterm in the second term's prior expansion and reports its rank. If the term is not contained at
   * all, -1 is returned, and -2 if the second term's expansion is empty. 0 is returned if the two terms are identical
   * strings.
   *
   * @param firstTerm the first term, which is used as a search key
   * @param secondTerm the second term, which will be expanded
   *
   * @return 0 if the two terms are identical; -2 if the second term has no expansion; -1 if the first term is not
   *         contained in the second term's expansion at all. Otherwise, the first term's position in the second
   *         term's expansion in natural ordering, meaning that 1 is the first element in the expansion list.
   */
  def positionInPriorExpansion(firstTerm: String, secondTerm: String): Int = {
    // if the terms are the same, no need to expand
    if (firstTerm == secondTerm) return 0

    val expansions = priorTermExpansion(secondTerm)

    if (expansions.isEmpty) return -2

    priorTermExpansionTrials += 1
    expansions.get(firstTerm) match {
      case Some(ExpansionIndexHolder(index, _)) =>
        priorTermExpansionFindings += 1
        if (priorTermExpansionFindingsFrequency != null) priorTermExpansionFindingsFrequency += firstTerm
        index + 1 // we report natural ordering (such that index 0 => term1 == term2 and index 1 => exp(term2)[0] ==
        // term1)
      case None => -1
    }
  }

  /**
   * Looks up the term of `needle` in the context-sensitively re-ranked expansion of `expandedMention`.
   *
   * If `usePartnerContext` is set to true, the context of `needle` is used to perform the re-ranking of the expansion
   * instead of the usual context of `expandedMention`. Doing so effectively renders caching useless, since instead of
   * `n` re-rankings (with `n` being the number of mentions in a document) in the worst case, this can lead to `n(n-1)`
   * re-rankings being performed. It is therefore not recommended to enable this option.
   *
   * If `filterContext` is enabled, only those context features will be used that are not part of the mention itself.
   *
   * @param needle the mention whose term is used as a lookup needle
   * @param expandedMention the mention whose term is expanded context-sensitively
   * @param usePartnerContext if true, uses the context fatures of `needle` for re-ranking; uses the context features of
   *                          `expandedMention` otherwise
   * @param filterContext if true, uses only context features not part of the mention that is the origin of the
   *                      context features
   *
   * @return 0 if the two terms are identical; -2 if the expansion is empty; -1 if the needle term is not contained;
   *         Otherwise, the first term's position in the second term's expansion in natural ordering, meaning that 1
   *         is the first element in the expansion list.
   */
  def positionInRerankedPriorExpansion(needle: Mention, expandedMention: Mention, usePartnerContext: Boolean,
                                       filterContext: Boolean): Int = {
    if (getTerm(needle) == getTerm(expandedMention)) 0
    else {
      val expansion = usePartnerContext match {
        case true => val ctx = if (filterContext) getOuterMentionContextFeaturesCache(needle) else getContext(needle)
          rerankedExpansion(getTerm(expandedMention), ctx)
        case false => val ctx = if (filterContext) getOuterMentionContextFeaturesCache(expandedMention) else
          getContext(expandedMention)
          rerankedExpansion(getTerm(expandedMention), ctx)
      }
      if (expansion.isEmpty) -2 else expansion.get(getTerm(needle)).map(_.index + 1).getOrElse(-1)
    }
  }

  /**
   * Returns the context features of a mention that contain no terms which are part of the mention itself.
   *
   * The result of this function is not necessarily a subset of [[getContext]]. For example, a trigram thesaurus might
   * want to create a trigram from the words before and after a mention and the head word.
   *
   * @param mention a mention for which the context features of this thesaurus had been already computed
   *
   * @return a set of context features
   */
  def computeOuterMentionContextFeatures(mention: Mention): Set[String]

  /**
   * Returns the context features of a mention that contain no terms which are part of the mention itself and
   * caches the result for future access.
   *
   * The result of this function is not necessarily a subset of [[getContext]]. For example, a trigram thesaurus might
   * want to create a trigram from the words before and after a mention and the head word.
   *
   * @param mention a mention for which the context features of this thesaurus had been already computed
   *
   * @return a set of context features
   */
  def getOuterMentionContextFeaturesCache(mention: Mention) = outerMentionContextFeaturesCache.getOrElseUpdate(mention,
    computeOuterMentionContextFeatures(mention))

  /**
   * Returns the prior expansion of `term` after performing context-sensitive re-ranking according to the given
   * set of context features. The elements of the expansion are weighted according to how well they fit the given
   * context, e.g. "Click the '''mouse'''" will favor computer-themed expansion terms.
   *
   * The algorithm works as follows. Let `rank(t)` be a function that assigns a real number to a term `t` and `C` the
   * set of context features. Further, let `sig(t, c)` return the significance of the term-feature pair stored in
   * the DT. `rank(t)` is then defined as the weighted harmonic mean of the individual, plus-one-smoothed `sig(t, c)`
   * values, with `c ∈ C` and the weight being the inverse observation frequency of the term in `c`. The elements of
   * the expansion are than re-sorted in decreasing order of `rank(t)`.
   *
   * For reasons of precision, the computation is performed in exponential space. You can obtain the original value
   * of `rank(t)` by raising e to the power of [[ExpansionIndexHolder.score]]. One exception is if `t` is identical
   * to the expanded term itself; in that case, [[Double.MaxValue]] is the value used.
   *
   * @param term the term to expand
   * @param context the context features to use for re-ranking
   *
   * @return the re-ranked expansion
   */
  def rerankedExpansion(term: String, context: Set[String]): mutable.LinkedHashMap[String,
    ExpansionIndexHolder] = {
    cache.rerankedExpansionCache(term, context) {
      val priorExpansions = priorTermExpansion(term)

      if (priorExpansions.isEmpty || context.isEmpty) priorExpansions
      else {
        val featureArray = context.toArray

        val priorExpansionTerms = priorExpansions.toSeq.map(_._1).toList

        val featureToTermToScore = featureArray.map(f => interface.getBatchTermContextsScore(term, f))

        val scores = priorExpansionTerms.toSeq map { term =>
          val nom = new LogAdder()
          val denom = new LogAdder()

          for (i <- 0 until featureArray.size) {
            val feature = featureArray(i)
            val featureTermCount = cache.termCountLogCache(feature)(Math.log(termCount(feature)))

            nom.logAdd(-featureTermCount)
            denom.logAdd(-Math.log(featureTermCount + DistributionalThesaurusComputer.javaMapGetOrElse(
              featureToTermToScore(i), term, new java.lang.Double(0.0)) + 1.0))
          }

          val score = nom.getSum - denom.getSum

          // to keep ranks symmetric with prior expansion, bring the identical term to the top
          if (term == term) (term, Double.MaxValue) else (term, score)
        }

        // re-sort
        mutable.LinkedHashMap.empty[String, ExpansionIndexHolder] ++= scores.view.sortBy(-_._2).zipWithIndex.map(tup
        => tup._1._1 -> ExpansionIndexHolder(tup._2, tup._1._2))
      }
    }
  }

  /**
   * Extracts the term found in a feature and returns how many times the term was seen during the thesaurus training.
   *
   * Most thesauri include on one or more terms in their context features. The standard dependency thesaurus, for
   * example, stores the partner of a dependency relation, e.g. `-det#the#DT` contains the term `the#DT`. The
   * implementation should extract this/these term(s) and consult the JoBimText API method `getTermCount()`.
   *
   * In case of multiple terms per feature it is up to the implementation to decide whether the counts should
   * be averaged, summed up, or else.
   *
   * Return 1 for all features that do not contain a term.
   *
   * @param feature a context feature
   *
   * @return the count of the term(s) in the given feature
   */
  def termCount(feature: String): Long

  /**
   * Returns the percentage of terms shared between the prior expansions of both mentions in relation to the size of
   * the smaller expansion. Returns [[None]] if
   * at least one of the mentions has an empty prior expansion.
   *
   * The value is calculated as `|expansion(t1) ∩ expansion(t2)| / min(|expansion(t1)|, |expansion(t2)|`, with
   * `t1`, `t2` being the terms of mentions one or two, respectively. The function is symmetric.
   *
   * @param first the first mention whose term will be prior expanded
   * @param second the second mention whose term will be prior expanded
   *
   * @return [[Some]] shared terms in percentage or [[None]] if at least one of the mentions has an empty
   *         prior expansion
   */
  def sharedPriorExpansionCount(first: Mention, second: Mention): Option[Double] = {
    val firstExpansions = priorTermExpansion(first)
    val secondExpansions = priorTermExpansion(second)
    val firstExpansionJos = firstExpansions.view.map(_._1)
    val secondExpansionJos = secondExpansions.view.map(_._1)
    if (firstExpansions.size == 0 || secondExpansions.size == 0) None
    else {
      val smallerExpansionList = if (firstExpansions.size < secondExpansions.size) firstExpansionJos
      else
        secondExpansionJos
      val biggerExpansionSet = (if (firstExpansions.size < secondExpansions.size) secondExpansionJos
      else
        firstExpansionJos).toSet
      val maxSharableExpansions = smallerExpansionList.size
      Some(smallerExpansionList.count(biggerExpansionSet.contains).toDouble / maxSharableExpansions.toDouble)
    }
  }

  /**
   * Expands only the context of the given mention. See
   * [[org.jobimtext.coref.berkeley.DistributionalThesaurusComputer.contextExpansion(Set[String]):mutable.LinkedHashMap[String,ExpansionIndexHolder] *]].
   *
   * @param mention the context of this mention's head will be expanded
   *
   * @return a linked hash map mapping terms to their index and probability in the context expansion
   */
  def contextExpansion(mention: Mention): mutable.LinkedHashMap[String, ExpansionIndexHolder] = {
    contextExpansion(getContext(mention))
  }

  /**
   * Expands the given set of context features, i.e. returns the terms that are to appear most likely in the given
   * context. The default implementation assumes the context features returned by the DT for a given term to be
   * conditionally independent of each other. In particular, knowing a feature A should not imply the existence of
   * a feature B, which could be the case e.g. in a mixed-gram model. If the holing operation of your thesaurus
   * does not fulfill this requirement, you have to re-implement this method using a proper language model.
   *
   * Returns a mapping from terms to their position in the context expansion and their probability of appearing in
   * the context in exponential space. The probability values are not normalized.
   *
   * The default implementation uses MLE and works as follows: Let `C` be the argument set of context features,
   * `sig(t,c)` return the significance of a term-feature pair stored in the DT, and `T` the set of terms for which
   * `∃ c ∈ C (sig(t,c) > 0)` holds. Then, calculate for each `t ∈ T` the probability `P(t|C)` as
   * `∏[c ∈ C] (sig(t,c) + 1)`. A normalizing denominator, which is the same for all `t`, is omitted. Next, the
   * terms in `T` are ordered in their decreasing likelihood and cut after [[maxPriorExpansionSize]] elements.

   * @param context the set of context features to expand
   *
   * @return a linked hash map map mapping from terms to their position in the context-based expansion and their
   *         not normalized probability.
   */
  def contextExpansion(context: Set[String]): mutable.LinkedHashMap[String,
    ExpansionIndexHolder] = cache.contextExpansionCache(context) {
    val contexts = context.toArray

    // mapping from feature (index) to all terms spanned by this feature and the respective term-feature significance
    // value
    val termScoresPerFeature = contexts.map(f => interface.getContextTermsScores(f).foldLeft(mutable.Map.empty[String,
      Double]) { case (map, pair) => map(pair.key) =
      pair.score.toDouble
      map
    }).toArray

    val allTerms = termScoresPerFeature.view.flatMap(_.keys).distinct

    val termToProbability = mutable.ListBuffer.empty[(String, Double)]

    // calculate P(jo|bim1,bim2,...) = P(jo|bim1) * P(jo|bim2) * ... * P(jo|bim_n)
    for (jo <- allTerms) {
      var probability = 0.0

      for (i <- 0 until termScoresPerFeature.length) {
        // smooth and add (we don't use the log probability defined as negative)
        probability += Math.log1p(termScoresPerFeature(i).getOrElse(jo, 0.0)) // - Math.log(sigSumPerBim(i) +
        // distinctBimCount)
      }

      termToProbability += Tuple2(jo, probability)
    }

    // sort by probability (in our logspace, higher values still mean higher probability)
    // also, take only max expansions, the very bad ones are only compatible with some contexts
    val sortedJos = termToProbability.sortBy(-_._2).take(maxPriorExpansionSize)

    val expansions = new mutable.LinkedHashMap[String, ExpansionIndexHolder]()
    expansions ++= sortedJos.view.zipWithIndex.map { case (termToScore, index) => (termToScore._1,
      ExpansionIndexHolder(index, termToScore._2))
    }

    expansions
  }

  /**
   * Expands the context of a given Mention using [[contextExpansion]] and returns the term with the highest rank
   * that is from an open word class.
   *
   * @param mention the mention whose context which will be expanded
   *
   * @return [[Some S o m e ( t e r m )]] if there was at least one term from an open class in the expansion,
   *        [[None]] otherwise
   */
  def contextExpansionOpenTerm(mention: Mention) = {
    contextExpansion(mention).collectFirst { case (term, _) if filterContextExpandedTerm(term) => term}
  }

  /**
   * Returns the position of the first mention's term in the second mention's context-based expansion (C-expansion).
   *
   * If the first mention's head is from a closed class, the first mention's term the result of
   * [[contextExpansionOpenTerm]] applied to the needle mention will be used as the search needle instead.
   *
   * This method returns [[None]] if the context expansion of a mention is empty (including the first mention, -1
   * if the term of `needle` was not found in the second mentions C-expansion, the position of the `needle` term
   * in the second mention's C-expansion otherwise.
   *
   * @param needle the first mention (search needle)
   * @param expandedContext the context of this mention will be expanded (search haystack)
   *
   * @return [[None]] if the context expansion of the second mention is empty, or if the first mention's term has
   *         been mapped to a term from an open class. [[Some S o m e ( - 1 )]] if the needle term used was not found in
   *         the C-expansion. Otherwise, returns Some(index) of the needle term used in the C-expansion using natural
   *         indices (i.e., 1 is the first element).
   */
  def positionInContextExpansion(needle: Mention, expandedContext: Mention) = {
    val searchTerm = if (needle.mentionType.isClosedClass) contextExpansionOpenTerm(needle) else Some(getTerm(needle))

    if (searchTerm.isEmpty || contextExpansion(expandedContext).isEmpty) {
      None
    }
    else {
      Some(contextExpansion(expandedContext).get(searchTerm.get).fold(-1)(_.index + 1))
    }
  }

  /**
   * First expands the context of the second mention as described in [[positionInContextExpansion]], then prior expands
   * the top `topExpanded` content words and reports the best rank of the first mentions term in any of these
   * arc 2 expansions, or -1 if the term was not contained. Content words are identified using
   * [[filterContextExpandedTerm]]. Note that the best rank is reported regardless of the position of the prior term
   * in the initial context-based expansion.
   *
   * Example:
   *
   * {{{
   *   C-EXPANSION of (eat *)
   *   food
   *    |
   *    -- burger
   *    -- pizza
   *    -- ...
   *   dust
   *    |
   *    -- rocks
   *    -- garbage
   * }}}
   *
   * Looking up "garbage" in this arc 2 expansion yields the rank 2, as it is the second item in the prior expansion
   * it was contained in.
   *
   * @param needleMention the mention whose term will be looked up. If it's head is from a closed class, the first
   *                      we use the first content word from its context expansion 
   * @param cExpandedMention the mention whose context will be expanded
   * @param topExpanded the number of top ranks to prior expand
   *
   * @return the highest rank from any of the prior expansions; -1 if the term was not contained in any of the
   *         arc 2 prior expansions; None if either the context expansion of `cExpandedMention` is empty or
   *         the first term was from a closed class but could not be mapped to an open term using
   *         [[contextExpansionOpenTerm]].
   */
  def positionInArc2ContextExpansion(needleMention: Mention, cExpandedMention: Mention, topExpanded: Int) = {
    val queryTerm = if (needleMention.mentionType.isClosedClass) contextExpansionOpenTerm(needleMention) else Some(getTerm(needleMention))

    val secondExpansion = contextExpansion(cExpandedMention)

    if (queryTerm.isEmpty || secondExpansion.isEmpty) {
      None
    } else {
      var topBestRank = Int.MaxValue

      for (contextJo <- secondExpansion.view.filter(tup => filterContextExpandedTerm(tup._1)).take(topExpanded)) {
        topBestRank = priorTermExpansion(contextJo._1).get(queryTerm.get).map(_.index).
          getOrElse(Int.MaxValue).min(topBestRank)
      }

      Some(if (topBestRank < Int.MaxValue) topBestRank else -1)
    }
  }

  /**
   * Returns the position of the mention's term in it's own context-based expansion. It is expected that pronouns are
   * ranked at the top for typical pleonastic expressions like "It rained".
   *
   * @param mention the mention whose context will be expanded
   *
   * @return Some([[ExpansionIndexHolder]]) if the term was in its own context-based expansion, [[None]]
   *         otherwise.
   */
  def positionInOwnContextExpansion(mention: Mention) = {
    contextExpansion(mention).get(getTerm(mention))
  }

  /**
   * Returns true if the given term is from an open word class. Thesauri which can't rely on a part of speech tag
   * stored in the Jo should use a list to exclude pronouns. If the holing operation uses multiple words for a term,
   * the method should return true if any of the words is from an open word class.
   *
   * If the class is indecidable, return true as a fallback.
   *
   * @param term The term
   *
   * @return true if the given term is from an open word class, false otherwise. Defaults to true for unknown cases
   */
  def filterContextExpandedTerm(term: String): Boolean

  /**
   * Looks up each attribute of the first mention in the second mention term's prior expansion and returns
   * the best (smallest number) rank or -1 if either the first mention has no attributes or the non of its attributes
   * was found in the expansion. Returns [[None]] if the second mention's prior expansion is empty.
   *
   * We consider as an "attribute" any property of a mention's head that was made opaque by the sentence it is
   * contained in. For example, in the sentence: "[Doctor Hunter] arrived at the scene",
   * "Doctor" is an attribute of the mention's head ("Hunter"). The extraction of attributes and their conversion
   * into term form is the responsibility of the underlying holing system implementation.
   *
   * @param headHasAttributesMention mention whose attributes will be used as lookup keys
   * @param expandedMention mention that will be expanded
   *
   * @return [[None]] if `expandedMention` has an empty prior expansion; [[Some Some(-1)]] if either the first
   *         mention has no attributes or none of its attributes was a member of the prior expansion. Returns
   *         [[Some Some(0)]] if any of the attribute terms is identical with the second mention's term. Otherwise,
   *         returns [[Some]] best rank among all attributes
   *
   * @see [[extractAttributesOfHead]]
   */
  def attributesInPriorExpansion(headHasAttributesMention: Mention, expandedMention: Mention): Option[Int] = {
    val attributes = extractAttributesOfHead(headHasAttributesMention)
    if (attributes.contains(getTerm(expandedMention))) Some(0) // no need to expand
    else if (attributes.size == 0) Some(-1)
    else {

      val expansions = priorTermExpansion(expandedMention)
      if (expansions.isEmpty) None
      else {
        attributeTermExpansionTrials += 1
        val rank = attributes.map(prop => expansions.get(prop).fold(Int.MaxValue)(_.index)).min
        if (rank != Int.MaxValue) attributeTermExpansionFindings += 1

        if (rank != Int.MaxValue) Some(rank) else Some(-1)
      }
    }
  }

  /**
   * Extracts all attributes of a mention's head in term form with relation to the underlying holing system. An
   * attribute is a property of a mention defined (explicitly or implicitly) in the same sentence. For example,
   * "President" is an attribute of "President Lincoln".
   *
   * The simplest way to do this is enumerating each non-head word in the mention that is an adjective or noun,
   * but the thesaurus may use more sophisticated strategies to detect attributes. It is recommended to use
   * dependency relations (like copula or appositive) to achieve the best results, even if the holing operation itself
   * does not make use of dependency parses. See [[BaseDoc.sentenceToWordToDependencies]] for accessing these
   * relations.
   *
   * @param mention the properties will be determined by this mention's head
   *                
   * @return a set of terms
   */
  def extractAttributesOfHead(mention: Mention): Set[String]

  /**
   * Returns the canonicalized (word-only) form of an ISA. Isas from the database often come with a count that
   * has to be removed to be compared for identity.
   *
   * @param isa the ISA string from the database interface
   *
   * @return the canonicalized ISA
   */
  def canonicalizeIsa(isa: String) = isa.split(':')(0).toLowerCase

  /**
   * Converts a term to its canoncial ISA (word only) representation. For example, a thesaurus with terms that are
   * composed of words and POS tags, the POS tag would be removed.
   *
   * @param term the term
   *
   * @return the isa representation of that term
   * @see [[termToIsaRepresentation(Mention)]]
   */
  def termToIsaRepresentation(term: String): String

  /**
   * Converts the mention's term to its canonical ISA (word only) representation. For example, a thesaurus with terms
   * that are composed of words and POS tags, the POS tag would be removed.
   *
   * @param mention the mention's head
   *
   * @return the isa representation of the mention's term
   * @see [[termToIsaRepresentation(String)]]
   */
  def termToIsaRepresentation(mention: Mention): String = termToIsaRepresentation(getTerm(mention))

  /**
   * Returns the sets of ISAs for the given mention's term in canonicalized form (using [[canonicalizeIsa]]).
   * Each set corresponds to a sense. Both the sense sequence and the individual isa sets may be empty. The order
   * of the senses and ISAs equals to the order they are retrieved from the thesaurus database, which may or may not
   * have semantics attached to them.
   *
   * @return a sequence of senses for the mention's term, each containing the corresponding isas
   */
  def isaSets(mention: Mention) = getSenses(getTerm(mention)).map(sense => mutable.LinkedHashSet
    (sense.getIsas.map(canonicalizeIsa): _*))

  /**
   * Returns the maximum Dice index of all possible pairings of ISA sense sets of the terms of the first and
   * second mention.
   *
   * The Dice index of two sets A and B is defined as (2 |A ∩ B|) / (|A| + |B|).
   *
   * @param first the first mention
   * @param second the second mention
   *
   * @return F1 mesaure of shared ISAs together with those shared ISAs
   */
  def headsSharedIsas(first: Mention, second: Mention): Option[(Double, Seq[String])] = {
    // compare every sense of the first mention with every sense of the second mention
    val firstIsas = isaSets(first)
    val secondIsas = isaSets(second)

    if (firstIsas.isEmpty || secondIsas.isEmpty) {
      None
    } else {
      var bestValue = 0.0
      var bestSharedIsas = new mutable.LinkedHashSet[String]

      for (isaArrayA <- firstIsas; isaArrayB <- secondIsas) {
        // using the Dice index
        val intersection = isaArrayA.intersect(isaArrayB) // note that the returned list's ordering is dependent on
        // this non-symmetrical operation
        val simMeasure = (2.0 * intersection.size) / (isaArrayA.size + isaArrayB.size).toDouble
        if (simMeasure > bestValue) {
          bestValue = simMeasure
          bestSharedIsas = intersection
        }
      }

      Some((bestValue, bestSharedIsas.toSeq))
    }
  }

  /**
   * Returns whether the second mention's term could be found among the ISA set of any sense cluster of the
   * first mention or [[None]] if the first mention's term has no ISAs in the DT.
   *
   * @param expandedMention the ISAs of this mention's head will be considered
   * @param possibleIsa the head of this mention will be tested
   *
   * @return [[Some Some(true)]] if the term of the second mention was among the ISAs of the first; [[Some Some(false)]]
   *        if not; [[None]] if the first mention has no ISAs in the DT
   */
  def headIsIsa(expandedMention: Mention, possibleIsa: Mention): Option[Boolean] = {
    val firstIsas = getSenses(getTerm(expandedMention)).flatMap(_.getIsas).map(canonicalizeIsa)
    if (firstIsas.isEmpty) {
      None
    } else {
      val termAsIsa = termToIsaRepresentation(getTerm(possibleIsa))
      Some(firstIsas.contains(termAsIsa))
    }
  }

  /**
   * Returns whether any of the attributes of the first mention have an ISA that is equal to the second mention's
   * term. See [[extractAttributesOfHead()]] for a description of attributes.
   *
   * If the first term has no attributes at all, [[None]] is returned. [[termToIsaRepresentation]] is used to
   * map the second mention's term to the canonicalized ISA representation.
   *
   * @param headHasAttributesMention this mention's attributes will be considered
   * @param possibleIsa this mention's term will be looked up in the attribute sets of the first mention
   *
   * @return [[None]] if the first mention has no attributes, Some(result) otherwise
   */

  def mentionIsAttributeIsa(headHasAttributesMention: Mention, possibleIsa: Mention): Option[Boolean] = {
    val attributes = extractAttributesOfHead(headHasAttributesMention)
    if (attributes.isEmpty) None
    else {
      val attributeIsas = attributes.flatMap(term => getSenses(term)).flatMap(_
        .getIsas).map(canonicalizeIsa)

      if (attributeIsas.contains(termToIsaRepresentation(getTerm(possibleIsa)))) Some(true) else Some(false)
    }
  }

  /**
   * Returns the prior term ("word") expansion of the given mention regardless of context.
   *
   * @param mention the mention that will be expanded
   *
   * @return a mapping from expansion terms to [[ExpansionIndexHolder ExpansionIndexHolders]]. The map is ordered,
   *         so that map.first returns the top ranked term.
   *
   * @see priorTermExpansion(String)
   */
  def priorTermExpansion(mention: Mention): mutable.LinkedHashMap[String, ExpansionIndexHolder] =
    priorTermExpansion(getTerm(mention))

  /**
   * Returns the prior term ("word") expansion of the given term regardless of context.
   *
   * @param term the term that will be expanded
   *
   * @return a mapping from expansion terms to [[ExpansionIndexHolder ExpansionIndexHolders]]. The map is ordered,
   *         so that map.first returns the top ranked term.
   */
  def priorTermExpansion(term: String): mutable.LinkedHashMap[String, ExpansionIndexHolder] = {
    def isCompatible(expansionTerm: String) = {
      config.dtRemoveIncompatibleTerms match {
        case false => true
        case true => getAntonymCount(term, expansionTerm) <= config.dtRemoveIncompatibleTermsK
      }
    }
    cache.priorTermExpansionCache(term) {
      val expansions = new mutable.LinkedHashMap[String, ExpansionIndexHolder]()
      expansions ++= interface.getSimilarTerms(term).view.filter(t => isCompatible(t.key)).zipWithIndex.map(tuple =>
        (tuple._1.key,
          ExpansionIndexHolder(tuple._2, tuple._1.score))).take(maxPriorExpansionSize)
      expansions
    }
  }

  /**
   * Returns the number of antonymic co-occurrences of the two mentions' terms. The counts are retrieved from
   * the antonym database, which has to be set up separately using the thesaurus configuration.
   *
   * The required data can (but does not have to be) obtained using patterns of incompatibility (see Dekang Lin et al.
   * (2003): "Identifying Synonyms among Distributionally Similar Word").
   *
   * @return the number of times the terms of the two mentions were seen co-occurring in patterns of incompatibility
   *         
   * @throws IllegalStateException If this object has not been provided with an antonym database connection
   */
  @throws[IllegalStateException]("If the antonym database has not been connected")
  def getAntonymCount(firstMention: Mention, secondMention: Mention): Int = {
    getAntonymCount(getTerm(firstMention), getTerm(secondMention))
  }

  /**
   * Returns the number of antonymic co-occurrences of the two terms. The counts are retrieved from
   * the antonym database, which has to be set up separately using the thesaurus configuration.
   *
   * The required data can (but does not have to be) obtained using patterns of incompatibility (see Dekang Lin et al.
   * (2003): "Identifying Synonyms among Distributionally Similar Word").
   *
   * @return the number of times the terms were seen co-occurring in patterns of incompatibility
   *         
   * @throws IllegalStateException If this object has not been provided with an antonym database connection
   */
  @throws[IllegalStateException]("If the antonym database has not been connected")
  def getAntonymCount(term1: String, term2: String): Int = {
    if (!antonymDatabase.isDefined) throw new IllegalStateException("Antonym Database not connected but isAntonym() " +
      "called!")

    antonymDatabase.get.getCount(term1, term2)
  }

  /**
   * Extracts the attributes used in [[incompatibleAttributes]]. Defaults to [[extractAttributesOfHead]].
   * 
   * @param mention the mention whose attributes are being extracted
   *                
   * @return a set of attribute terms
   */
  def extractIncompatibleAttributesOfHead(mention: Mention): Set[String] = extractAttributesOfHead(mention)

  /**
   * Compares the attributes of the two mentions with identical terms and reports whether any set of them is
   * incompatible by means of a [[AttributeIncompatibilityResult]]. This allows, for example, to distinguish
   * "the west coast" from "the east coast".
   * 
   * A set of terms is considered incompatible if its [[getAntonymCount antonym count]] is greater than
   * [[CorefSystemConfiguration.dtRemoveIncompatibleTermsK a threshold k]]. The result semantics are as follows:
   * 
   * <ul>
   * <li> [[AttributeIncompatibilityResult.UnequalTerms]] if the terms of both mentions are not identical</li>
   * <li> [[AttributeIncompatibilityResult.BothNoAttributes]] if neither mention has attributes</li>
   * <li> [[AttributeIncompatibilityResult.BothNoAttributes]] if one mention has no attributes</li>
   * <li> [[AttributeIncompatibilityResult.Incompatible]] if the antonym database provides evidence that both terms
   * are incompatible</li>
   * <li> [[AttributeIncompatibilityResult.Unknown]] the default case</li>
   * </ul>
   * 
   * @return a [[AttributeIncompatibilityResult]] with the semantics described above
   */
  def incompatibleAttributes(firstMention: Mention, secondMention: Mention) = {
    if (getTerm(firstMention) != getTerm(secondMention)) AttributeIncompatibilityResult.UnequalTerms
    else {
      val firstProps = extractIncompatibleAttributesOfHead(firstMention)
      val secondProps = extractIncompatibleAttributesOfHead(secondMention)
      if (firstProps.isEmpty && secondProps.isEmpty) AttributeIncompatibilityResult.BothNoAttributes
      else if (firstProps.isEmpty || secondProps.isEmpty) AttributeIncompatibilityResult.OneNoAttributes
      else {
        var result = AttributeIncompatibilityResult.Unknown
        for (prop1 <- firstProps; prop2 <- secondProps) {
          if (getAntonymCount(prop1, prop2) > config.dtRemoveIncompatibleTermsK) result =
            AttributeIncompatibilityResult.Incompatible
        }
        result
      }
    }
  }

  /**
   * Returns all sense clusters of a given term.
   *
   * @param term the term
   *
   * @return an sequence of sense clusters in the order they are returned from the database interface.
   */
  def getSenses(term: String) = {
    cache.sensesCache(term) {
      val senses = interface.getSenses(term)
      senses.toArray(new Array[Sense](senses.size()))
    }
  }

  /**
   * Disambiguates the sense of a term. If the term's set of senses is empty, [[None]] is returned.
   *
   * The implementation uses a credit algorithm. First, it performs context-sensitive re-ranking on the `term`
   * given the `context`. Then, for each element in the expansion, all senses that contain this element in the
   * cluster ([[org.jobimtext.api.struct.Sense.getSenses]]) get credited. The credits decrease if one goes down
   * the expansion ranks. The credits are then normalized by the size of the sense cluster and the sense with
   * the highest points is returned.
   *
   * @example Assume the term "Oscar" and two senses, one meaning the award, and one meaning a person of the same
   *          name. After context-sensitive re-ranking, award-related terms are ranked at the top, thus the award
   *          sense gets more credits.
   *
   * @param term the term whose senses should be disambiguated
   * @param context the context in which the term should be disambiguated
   *
   * @return
   */
  def disambiguateSense(term: String, context: Set[String]): Option[Sense] = {
    val senses = getSenses(term).toArray
    if (senses.isEmpty) None
    else if (senses.size == 1) Some(senses(0))
    else {
      val expansion = rerankedExpansion(term, context)
      val senseScores = mutable.Map.empty[String, Int].withDefaultValue(0)
      val clusterSets = senses.map(_.getSenses.toSet)
      val alpha = 1
      var currentRankScore = 0
      for (expansionTerm <- expansion) {
        for (i <- 0 until senses.size) {
          if (clusterSets(i).contains(expansionTerm._1)) senseScores(senses(i).getCui) = senseScores(senses(i)
            .getCui) + currentRankScore
        }
        currentRankScore -= alpha
      }

      val highestSense = senses.maxBy(sense => senseScores(sense.getCui) / sense.getSenses.size)

      Some(highestSense)
    }
  }
}

object DistributionalThesaurusComputer {

  /**
   * Explains the result of [[DistributionalThesaurusComputer.incompatibleAttributes]].
   */
  object AttributeIncompatibilityResult extends Enumeration {
    type AttributeIncompatibilityResult = Value
    val UnequalTerms, BothNoAttributes, OneNoAttributes, Incompatible, Unknown = Value
  }

  /**
   * Mimics [[scala.collection.Map.getOrElse]].
   */
  def javaMapGetOrElse[K, V](theMap: java.util.Map[K, V], theKey: K, theElse: => V): V = {
    if (theMap.containsKey(theKey)) theMap.get(theKey) else theElse
  }
}