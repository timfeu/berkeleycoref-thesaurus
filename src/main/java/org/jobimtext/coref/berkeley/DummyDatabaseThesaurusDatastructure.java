package org.jobimtext.coref.berkeley;

import org.jobimtext.api.db.DatabaseResource;
import org.jobimtext.api.struct.IThesaurusDatastructure;
import org.jobimtext.api.struct.Order1;
import org.jobimtext.api.struct.Order2;
import org.jobimtext.api.struct.Sense;

import java.util.*;

/**
 * Thesaurus API that simulates talking to an "empty" thesaurus.
 *
 * @author Tim Feuerbach
 */
public class DummyDatabaseThesaurusDatastructure extends DatabaseResource implements IThesaurusDatastructure<String, String> {

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public void destroy() {
        // do nothing
    }

    /**
     * Returns 1000 if both terms are identical, 0 otherwise.
     */
    @Override
    public Double getSimilarTermScore(String t1, String t2) {
        if (t1.equals(t2)) {
            return 1000.0;
        } else {
            return 0.0;
        }
    }

    /**
     * Returns a list containing only the key itself.
     */
    @Override
    public List<Order2> getSimilarTerms(String key) {
        return Collections.singletonList(new Order2(key, 1.0));
    }

    /**
     * Returns a list containing only the key itself.
     *
     * @param numberOfEntries if <= 0, the list will be empty
     */
    @Override
    public List<Order2> getSimilarTerms(String key, int numberOfEntries) {
        if (numberOfEntries <= 0) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new Order2(key, 1.0));
        }
    }

    /**
     * Returns a list containing only the key itself.
     */
    @Override
    public List<Order2> getSimilarTerms(String key, double threshold) {
        return Collections.singletonList(new Order2(key, 1.0));
    }

    /**
     * Returns a list containing only the key itself.
     */
    @Override
    public List<Order2> getSimilarContexts(String values) {
        return Collections.singletonList(new Order2(values, 1.0));
    }

    /**
     * Returns a list containing only the key itself.
     *
     * @param numberOfEntries if <= 0, the list will be empty
     */
    @Override
    public List<Order2> getSimilarContexts(String values, int numberOfEntries) {
        if (numberOfEntries <= 0) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new Order2(values, 1.0));
        }
    }

    /**
     * Returns a list containing only the key itself.
     */
    @Override
    public List<Order2> getSimilarContexts(String values, double threshold) {
        return Collections.singletonList(new Order2(values, 1.0));
    }

    /**
     * Returns 1.
     */
    @Override
    public Long getTermCount(String key) {
        return 1L;
    }

    /**
     * Returns 1.
     */
    @Override
    public Long getContextsCount(String key) {
        return 1L;
    }

    /**
     * Returns 1.
     */
    @Override
    public Long getTermContextsCount(String key, String val) {
        return 1L;
    }

    /**
     * Returns 1.
     */
    @Override
    public Double getTermContextsScore(String key, String val) {
        return 1.0;
    }

    /**
     * Returns an empty list.
     */
    @Override
    public Map<String, Double> getBatchTermContextsScore(String key, String context) {
        HashMap<String, Double> result = new HashMap<String, Double>();
        return result;
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Order2> getContextTermsScores(String feature) {
        return Collections.emptyList();
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Order1> getTermContextsScores(String key) {
        return Collections.emptyList();
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Order1> getTermContextsScores(String key, int numberOfEntries) {
        return Collections.emptyList();
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Order1> getTermContextsScores(String key, double threshold) {
        return Collections.emptyList();
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Sense> getSenses(String key) {
        return Collections.emptyList();
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Sense> getIsas(String key) {
        return Collections.emptyList();
    }

    /**
     * Returns an empty list.
     */
    @Override
    public List<Sense> getSenseCUIs(String key) {
        return Collections.emptyList();
    }
}
