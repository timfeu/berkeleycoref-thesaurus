package edu.berkeley.nlp.coref;

/**
 * Enumerations of possible feature conjunction strategies.
 */
public enum ConjType {
    /**
     * No conjunctions.
     */
    NONE,

    /**
     * The mention type, one of {@link edu.berkeley.nlp.coref.MentionType}.
     */
    TYPE,

    TYPE_OR_RAW_PRON, CANONICAL, CANONICAL_NOPRONPRON, CANONICAL_ONLY_PAIR_CONJ, CANONICAL_OR_COMMON, IS_NER_OR_POS;
}
