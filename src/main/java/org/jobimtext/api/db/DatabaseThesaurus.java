package org.jobimtext.api.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.jobimtext.api.IThesaurus;


public class DatabaseThesaurus extends DatabaseResource
        implements
        IThesaurus<String, String, ResultSet, ResultSet, ResultSet, ResultSet, ResultSet> {

    /*private static final int SINGLE_BATCH = 1;
    private static final int SMALL_BATCH = 4;
    private static final int MEDIUM_BATCH = 11;
    private static final int LARGE_BATCH = 51;
    private static final int LARGER_BATCH = 117;
    private static final int MAX_BATCH = 200;*/

    @Override
    public ResultSet getSimilarTerms(String key) {
        String sql = getDatabaseConfiguration().getSimilarTermsQuery();
        try {


            PreparedStatement ps;
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getSimilarTerms(String key, int numberOfEntries) {
        String sql = getDatabaseConfiguration().getSimilarTermsTopQuery(
                numberOfEntries);
        try {

            PreparedStatement ps = getDatabaseConnection().getConnection()
                    .prepareStatement(sql);
            ps.setString(1, key);
            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getSimilarTerms(String key, double threshold) {
        String sql = getDatabaseConfiguration().getSimilarTermsGtScoreQuery();

        try {
            PreparedStatement ps = getDatabaseConnection().getConnection()
                    .prepareStatement(sql);
            ps.setString(1, key);
            ps.setDouble(2, threshold);
            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getSimilarContexts(String values) {
        String sql = getDatabaseConfiguration().getSimilarContextsQuery();

        try {
            PreparedStatement ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, values);
            return ps.executeQuery();
        } catch (SQLException e) {

            System.err.println(e.getMessage());
            System.err.println(values);
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getSimilarContexts(String values, int numberOfEntries) {
        String sql = getDatabaseConfiguration().getSimilarContextsTopQuery(numberOfEntries);

        try {

            PreparedStatement ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, values);

            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println(values);
            System.err.println("Query: " + sql);
        }
        return null;
    }


    @Override
    public ResultSet getSimilarContexts(String values, double threshold) {
        String sql = getDatabaseConfiguration().getSimilarContextsGtScoreQuery();

        try {

            PreparedStatement ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, values);
            ps.setDouble(2, threshold);

            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public Long getTermCount(String key) {
        Long count = 0L;
        String sql = getDatabaseConfiguration().getTermsCountQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);

            ps.setString(1, key);
            ResultSet set = ps.executeQuery();

            if (set.next()) {
                count = set.getLong(1);
            }
            ps.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return count;
    }

    @Override
    public Long getContextsCount(String values) {
        Long count = 0L;
        String sql = getDatabaseConfiguration().getContextsCountQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, values);
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                count = set.getLong(1);
            }
            ps.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return count;
    }

    @Override
    public Long getTermContextsCount(String key, String values) {
        Long count = 0L;
        String sql = getDatabaseConfiguration().getTermContextsCountQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ps.setString(2, values);
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                count = set.getLong(1);
            }
            ps.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return count;
    }

    @Override
    public Double getTermContextsScore(String key, String val) {
        double score = 0.0;
        String sql = getDatabaseConfiguration().getTermContextsScoreQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ps.setString(2, val);
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                score = set.getDouble(1);

            }
            ps.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return score;
    }

    /*private String buildBatchInClause(int batchSize) {
        StringBuilder sb = new StringBuilder();

        sb.append("(");
        for (int i = 0; i < batchSize; i++) {
            sb.append("?");
            if (i + 1 < batchSize) sb.append(",");
        }
        sb.append(")");

        return sb.toString();
    }*/

    /*@Override
    public Map<String, Double> getBatchTermContextsScore(List<String> keys, String context) {
        HashMap<String, Double> result = new HashMap<String, Double>();

        // initialize in case we get empty rows
        for (String key : keys) {
            result.put(key, 0.0);
        }

        int totalNumberOfValuesLeftToBatch = keys.size();

        try {
            int currentIndex = 0;
            while (totalNumberOfValuesLeftToBatch > 0) {

                int batchSize = SINGLE_BATCH;
                if (totalNumberOfValuesLeftToBatch >= MAX_BATCH) {
                    batchSize = MAX_BATCH;
                } else if (totalNumberOfValuesLeftToBatch >= LARGER_BATCH) {
                    batchSize = LARGER_BATCH;
                } else if (totalNumberOfValuesLeftToBatch >= LARGE_BATCH) {
                    batchSize = LARGE_BATCH;
                } else if (totalNumberOfValuesLeftToBatch >= MEDIUM_BATCH) {
                    batchSize = MEDIUM_BATCH;
                } else if (totalNumberOfValuesLeftToBatch >= SMALL_BATCH) {
                    batchSize = SMALL_BATCH;
                }
                totalNumberOfValuesLeftToBatch -= batchSize;

                //System.out.println("Batch size: " + batchSize);

                String sql = getDatabaseConfiguration().getBatchTermContextsScoreQuery(buildBatchInClause(batchSize));
                PreparedStatement ps = getDatabaseConnection().getConnection().prepareStatement(sql);

                ps.setString(1, context);

                for (int i = 0; i < batchSize; i++) {
                    ps.setString(i + 2, keys.get(currentIndex + i));
                }
                currentIndex = currentIndex + batchSize;

                //System.out.println("execute query");
                ResultSet set = ps.executeQuery();

                //System.out.println("retrieve");
                if (set.next()) {
                    result.put(set.getString(1), set.getDouble(2));
                }
                //System.out.println("fin");

                ps.close();

            }
        } catch (SQLException e) {
            throw new IllegalStateException("Can't run SQL statement", e);
        }


        PreparedStatement ps;

        return result;
    }*/

    public Map<String, Double> getBatchTermContextsScore(String expandedJo, String context) {
        HashMap<String, Double> result = new HashMap<String, Double>();

        String sql = getDatabaseConfiguration().getBatchTermContextsScoreQuery();
        try {
            PreparedStatement ps = getDatabaseConnection().getConnection().prepareStatement(sql);

            ps.setString(1, expandedJo);
            ps.setString(2, context);

            ResultSet set = ps.executeQuery();

            while (set.next()) {
                result.put(set.getString(1), set.getDouble(2));
            }

            ps.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Can't run SQL statement " + sql, e);
        }

        return result;
    }

    @Override
    public ResultSet getContextTermsScores(String feature) {
        String sql = getDatabaseConfiguration().getContextTermsScoresQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, feature);
            ResultSet set = ps.executeQuery();

            return set;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResultSet getTermContextsScores(String key) {
        String sql = getDatabaseConfiguration().getTermContextsScoresQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ResultSet set = ps.executeQuery();

            return set;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getTermContextsScores(String key, int numberOfEntries) {
        String sql;
        sql = getDatabaseConfiguration().getTermContextsScoresTopQuery(numberOfEntries);
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ResultSet set = ps.executeQuery();

            return set;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("  Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getTermContextsScores(String key, double threshold) {
        String sql = getDatabaseConfiguration().getTermContextsScoresGtScore();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ps.setDouble(2, threshold);
            ResultSet set = ps.executeQuery();
            return set;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getSenses(String key) {
        String sql = getDatabaseConfiguration().getSensesQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ResultSet set = ps.executeQuery();

            return set;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getIsas(String key) {
        String sql = getDatabaseConfiguration().getIsasQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ResultSet set = ps.executeQuery();

            return set;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public ResultSet getSenseCUIs(String key) {
        String sql = getDatabaseConfiguration().getSensesCUIsQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, key);
            ResultSet set = ps.executeQuery();

            return set;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return null;
    }

    @Override
    public Double getSimilarTermScore(String t1, String t2) {
        double score = 0.0;
        String sql = getDatabaseConfiguration().getSimilarTermScoreQuery();
        PreparedStatement ps;
        try {
            ps = getDatabaseConnection().getConnection().prepareStatement(sql);
            ps.setString(1, t1);
            ps.setString(2, t2);
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                score = set.getDouble(1);

            }
            ps.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            System.err.println("Query: " + sql);
        }
        return score;
    }

}
