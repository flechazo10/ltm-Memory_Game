package server.dao;


import constants.Status;
import entity.Player;
import javafx.util.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PlayerDAO extends DAO {

    public PlayerDAO() throws SQLException {
        super();
    }

    public boolean createPlayer(String username, String password) throws SQLException {
        System.out.println("Creating player " + username + " with password " + password);
        String checkQuery = "SELECT COUNT(*) FROM tblPlayer WHERE username = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
        checkStmt.setString(1, username);
        ResultSet rs = checkStmt.executeQuery();

        System.out.println(rs);

        if (rs.next()) {
            int count = rs.getInt(1);
            if (count > 0) {
                System.out.println("Username đã tồn tại!");  // count = 1
            } else {
                System.out.println("Username có thể dùng!");  // count = 0
            }
        }

        String insertQuery = "INSERT INTO tblPlayer (username, password, status, totalScore) VALUES (?, ?, 'offline', 0)";
        PreparedStatement insertStmt = conn.prepareStatement(insertQuery);
        insertStmt.setString(1, username);
        insertStmt.setString(2, password);

        int rowsAffected = insertStmt.executeUpdate();
        return rowsAffected > 0;
    }


    public List<Player> getRankingList() throws SQLException {
        List<Player> rankingList = new ArrayList<>();
        String sql = "SELECT id, username, password, status, totalScore " +
                "FROM tblplayer ORDER BY totalScore DESC";

        try (PreparedStatement stm = conn.prepareStatement(sql);
             ResultSet rs = stm.executeQuery()) {

            int rank = 1;
            while (rs.next()) {
                Player p = new Player();
                p.setId(rs.getInt("id"));
                p.setUsername(rs.getString("username"));
                p.setPassword(rs.getString("password"));
                p.setStatus(rs.getString("status"));
                p.setTotalScore(rs.getDouble("totalScore"));
                p.setRank(rank++);            // Tính thứ hạng dựa trên tổng điểm
                rankingList.add(p);
            }
        }
        return rankingList;
    }

    public List<Player> getHistoryList() throws SQLException {
        List<Player> historyList = new ArrayList<>();
        String sql = "SELECT id, username, password, status, totalScore " +
                "FROM tblplayer ORDER BY totalScore DESC";

        try (PreparedStatement stm = conn.prepareStatement(sql);
             ResultSet rs = stm.executeQuery()) {

            int rank = 1;
            while (rs.next()) {
                Player p = new Player();
                p.setId(rs.getInt("id"));
                p.setUsername(rs.getString("username"));
                p.setPassword(rs.getString("password"));
                p.setStatus(rs.getString("status"));
                p.setTotalScore(rs.getDouble("totalScore"));
                historyList.add(p);
            }
        }
        return historyList;
    }

    public Player getDetailPlayer(String username) throws SQLException {
        String sql = "SELECT * FROM tblPlayer WHERE username = ?";
        PreparedStatement stm = conn.prepareStatement(sql);
        stm.setString(1, username);
        ResultSet rs = stm.executeQuery();

        Player player = null;
        if (rs.next()) {
            player = new Player(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getDouble("totalScore"), rs.getString("status"));
        }
        return player;
    }

    public void updatePlayerStatus(int playerId, String status) throws SQLException {
        String query = "UPDATE tblPlayer SET status = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, status);
        stmt.setInt(2, playerId);
        int rowsAffected = stmt.executeUpdate();
        System.out.println("rows affected: " + rowsAffected);
        if (rowsAffected == 0) {
            System.out.println("Not found player with id: " + playerId);
        }
        stmt.executeUpdate();
    }

    public List<Player> getAllPlayers() throws SQLException {
        List<Player> players = new ArrayList<>();
        String query = "SELECT * FROM tblPlayer";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        while (rs.next()) {
            Player player = new Player(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getDouble("totalScore"), rs.getString("status"));
            players.add(player);
//            System.out.println("Loaded player: ID=" + player.getId() + ", Username=" + player.getUsername() + ", Status=" + player.getStatus());
        }
        return players;
    }

    // Update diem cua nguoi choi sau tran
    public void updatePlayerElo(Player player, double eloChange) {
        String sql = "UPDATE tblPlayer SET totalScore = totalScore + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, eloChange);
            ps.setInt(2, player.getId());

            int rowUpdaetd  = ps.executeUpdate();
            System.out.println("row updaetd: " + rowUpdaetd);
            if (rowUpdaetd > 0) {
                player.setTotalScore(player.getTotalScore() + eloChange);
                System.out.println("[OK] Updated player's total score: " + player.getTotalScore() + ", id=" + player.getUsername());

            } else  {
                System.out.println("Not found player with id: " + player.getId());
            }
        } catch (SQLException e) {
            System.out.println("Loi khi cap nhat diem cho Player " + player);
            e.printStackTrace();
        }
    }

    public Pair<Player, Boolean> authenticate(String username, String password) throws SQLException {
        String query = "SELECT * FROM tblPlayer WHERE username = ? AND password = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, username);
        stmt.setString(2, password);

        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            Player authenticatePlayer = new Player(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getDouble("totalScore"), rs.getString("status"));
            Boolean isOffline = rs.getString("status").equalsIgnoreCase(String.valueOf(Status.OFFLINE));
            return new Pair<>(authenticatePlayer, isOffline);
        }
        return new Pair<>(null, null);
    }

    public static void main(String[] args) {
        try {
            PlayerDAO dao = new PlayerDAO();
            Player p = dao.getDetailPlayer("kien");
            if (p != null) {
                System.out.println("Người chơi: " + p.getUsername());
            } else {
                System.out.println("Không tìm thấy người chơi!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}