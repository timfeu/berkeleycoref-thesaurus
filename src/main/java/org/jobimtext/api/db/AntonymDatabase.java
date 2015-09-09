package org.jobimtext.api.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AntonymDatabase implements Destroyable {
    private DatabaseConnection connection;

    public AntonymDatabase() {
        connection = new DatabaseConnection();
    }

    public void connect(String url, String user, String password, String driver) throws SQLException, ClassNotFoundException {
        connection.openConnection(url, user, password, driver);
    }

    @Override
    public void destroy() {
        if (connection != null) connection.closeConnection();
    }

    public int getCount(String jo1, String jo2) throws SQLException {
        int count = 0;

        String sql = "SELECT `COUNT` FROM COHYPONYMS WHERE WORD1 = ? AND WORD2 = ?";
        PreparedStatement ps = connection.getConnection().prepareStatement(sql);
        ps.setString(1, jo1);
        ps.setString(2, jo2);

        ResultSet set = ps.executeQuery();

        if (set.next()) {
            count = set.getInt(1);
        }

        ps.close();

        return count;
    }

    public int getCount(String jo) throws SQLException {
        int count = 0;

        String sql = "SELECT SUM(`COUNT`) FROM COHYPONYMS WHERE WORD1 = ?";
        PreparedStatement ps = connection.getConnection().prepareStatement(sql);
        ps.setString(1, jo);

        ResultSet set = ps.executeQuery();

        if (set.next()) {
            count = set.getInt(1);
        }

        ps.close();

        return count;
    }
}
