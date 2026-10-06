package server.dao;

import entity.DetailMatch;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DetailMatchDAO extends DAO {
    public DetailMatchDAO() throws SQLException {
        super();
    }

    public boolean addDetailMatch(DetailMatch detail) {
        String sql = "INSERT INTO tblDetailMatch (match_id, player_id, points, is_quit, result) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, detail.getMatch().getId());
            ps.setInt(2, detail.getPlayer().getId());
            ps.setInt(3, detail.getPoints());
            ps.setBoolean(4, detail.isQuit());
            ps.setString(5, detail.getResult().name()); // enum -> string

            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi thêm DetailMatch: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

