package server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DAO {

    private static final String URL_JDBC = "jdbc:mysql://localhost:3306/LTM";
    private static final String URL_USER = "root";
    private static final String URL_PASS = "Trieu1312@";

    protected static Connection conn;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public DAO() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(URL_JDBC, URL_USER, URL_PASS);
        }
    }
}