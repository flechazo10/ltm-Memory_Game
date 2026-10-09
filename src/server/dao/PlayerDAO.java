package server.dao;

import constants.Status;
import entity.Player;
import javafx.util.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PlayerDAO extends DAO {

    public PlayerDAO() throws SQLException {
        super();
    }

    /** Đọc 1 dòng tblplayer thành Player (dùng chung cho mọi query). */
    private Player mapPlayer(ResultSet rs) throws SQLException {
        Player p = new Player();
        p.setId(rs.getInt("id"));
        p.setUsername(rs.getString("username"));
        p.setPassword(rs.getString("password"));
        p.setStatus(rs.getString("status"));
        p.setTotalScore(rs.getDouble("totalScore"));
        p.setWinCount(rs.getInt("winCount"));
        return p;
    }

    /** Đăng ký. Trả về false nếu username đã tồn tại. */
    public boolean createPlayer(String username, String password) throws SQLException {
        String checkQuery = "SELECT COUNT(*) FROM tblplayer WHERE username = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            checkStmt.setString(1, username);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println("[DAO] Username đã tồn tại: " + username);
                    return false;
                }
            }
        }

        String insertQuery = "INSERT INTO tblplayer (username, password, status, totalScore, winCount) "
                + "VALUES (?, ?, 'offline', 0, 0)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
            insertStmt.setString(1, username);
            insertStmt.setString(2, password);
            return insertStmt.executeUpdate() > 0;
        }
    }

    /**
     * Bảng xếp hạng toàn server:
     * tổng điểm giảm dần, bằng điểm thì số trận thắng giảm dần.
     */
    public List<Player> getRanking() throws SQLException {
        List<Player> rankingList = new ArrayList<>();
        String sql = "SELECT id, username, password, status, totalScore, winCount "
                + "FROM tblplayer ORDER BY totalScore DESC, winCount DESC, username ASC";

        try (PreparedStatement stm = conn.prepareStatement(sql);
             ResultSet rs = stm.executeQuery()) {
            int rank = 1;
            while (rs.next()) {
                Player p = mapPlayer(rs);
                p.setRank(rank++);
                rankingList.add(p);
            }
        }
        return rankingList;
    }

    /** Tên cũ, giữ lại để code Server đang gọi không bị lỗi. */
    public List<Player> getRankingList() throws SQLException {
        return getRanking();
    }

    /** Danh sách người chơi cho màn History (click vào để xem chi tiết từng trận). */
    public List<Player> getHistoryList() throws SQLException {
        List<Player> historyList = new ArrayList<>();
        String sql = "SELECT id, username, password, status, totalScore, winCount "
                + "FROM tblplayer ORDER BY username ASC";

        try (PreparedStatement stm = conn.prepareStatement(sql);
             ResultSet rs = stm.executeQuery()) {
            while (rs.next()) {
                historyList.add(mapPlayer(rs));
            }
        }
        return historyList;
    }

    public Player getDetailPlayer(String username) throws SQLException {
        String sql = "SELECT * FROM tblplayer WHERE username = ?";
        try (PreparedStatement stm = conn.prepareStatement(sql)) {
            stm.setString(1, username);
            try (ResultSet rs = stm.executeQuery()) {
                return rs.next() ? mapPlayer(rs) : null;
            }
        }
    }

    public void updatePlayerStatus(int playerId, String status) throws SQLException {
        String query = "UPDATE tblplayer SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, status);
            stmt.setInt(2, playerId);
            if (stmt.executeUpdate() == 0) {
                System.out.println("[DAO] Không tìm thấy player id: " + playerId);
            }
        }
    }

    public List<Player> getAllPlayers() throws SQLException {
        List<Player> players = new ArrayList<>();
        String query = "SELECT * FROM tblplayer";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                players.add(mapPlayer(rs));
            }
        }
        return players;
    }

    /** Chỉ lấy người đang online (rảnh hoặc đang trong trận) để hiển thị ở sảnh. */
    public List<Player> getOnlinePlayers() throws SQLException {
        List<Player> players = new ArrayList<>();
        String query = "SELECT * FROM tblplayer WHERE LOWER(status) <> 'offline'";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                players.add(mapPlayer(rs));
            }
        }
        return players;
    }

    /**
     * Cộng điểm xếp hạng sau trận: thắng +1, hòa +0.5, thua 0.
     * Thắng (eloChange >= 1) thì tự tăng winCount.
     */
    public void updatePlayerElo(Player player, double eloChange) {
        boolean isWin = eloChange >= 1;
        String sql = "UPDATE tblplayer SET totalScore = totalScore + ?, winCount = winCount + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, eloChange);
            ps.setInt(2, isWin ? 1 : 0);
            ps.setInt(3, player.getId());

            if (ps.executeUpdate() > 0) {
                player.setTotalScore(player.getTotalScore() + eloChange);
                if (isWin) {
                    player.setWinCount(player.getWinCount() + 1);
                }
                System.out.println("[DAO] Cập nhật điểm " + player.getUsername()
                        + ": totalScore=" + player.getTotalScore() + ", winCount=" + player.getWinCount());
            } else {
                System.out.println("[DAO] Không tìm thấy player id: " + player.getId());
            }
        } catch (SQLException e) {
            System.out.println("[DAO] Lỗi khi cập nhật điểm cho " + player);
            e.printStackTrace();
        }
    }

    /**
     * Xác thực đăng nhập.
     * Trả về Pair(player, isOffline): player = null nếu sai tài khoản/mật khẩu,
     * isOffline = false nếu tài khoản đang đăng nhập ở nơi khác.
     */
    public Pair<Player, Boolean> authenticate(String username, String password) throws SQLException {
        String query = "SELECT * FROM tblplayer WHERE username = ? AND password = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Player player = mapPlayer(rs);
                    String status = player.getStatus();
                    boolean isOffline = status == null
                            || status.equalsIgnoreCase(String.valueOf(Status.OFFLINE));
                    return new Pair<>(player, isOffline);
                }
            }
        }
        return new Pair<>(null, null);
    }
}
