package server.dao;

import entity.DetailMatch;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DetailMatchDAO extends DAO {

    public DetailMatchDAO() throws SQLException {
        super();
    }

    /** Ghi điểm xếp hạng (1 / 0.5 / 0), kết quả và cờ thoát ngang của 1 người chơi sau trận. */
    public boolean addDetailMatch(DetailMatch detail) {
        String sql = "INSERT INTO tbldetailmatch (match_id, player_id, points, is_quit, result) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, detail.getMatch().getId());
            ps.setInt(2, detail.getPlayer().getId());
            ps.setDouble(3, detail.getPoints());
            ps.setBoolean(4, detail.isQuit());
            ps.setString(5, detail.getResult().name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi khi thêm DetailMatch: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
