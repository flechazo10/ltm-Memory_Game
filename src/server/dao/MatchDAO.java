/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server.dao;

import constants.DetailMatchResult;
import constants.MatchStatus;
import entity.DetailMatch;
import entity.Match;
import entity.Player;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ngotu
 */
public class MatchDAO extends DAO{
    public MatchDAO() throws SQLException {
        super();
    }
    //Tao tran dau

    public int createMatch(Match match) throws SQLException {
        String sql = "INSERT INTO tblmatch (player1_id, player2_id, theme, status, start_time,time_limit) VALUES (?, ?, ?, ?, ?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, match.getPlayer1Id());
            ps.setInt(2, match.getPlayer2Id());
            ps.setString(3, match.getTheme());
            ps.setString(4, match.getStatus().toString());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(6, match.getTimeLimit());
            int rowAffected = ps.executeUpdate();

            if (rowAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    public List<DetailMatch> getHistoryDetailByPlayerId(int playerId) {
        List<DetailMatch> historyList = new ArrayList<>();
        String sql = """
            SELECT 
                m.id AS matchId,
                m.theme,
                m.status AS matchStatus,
                m.winner_id AS winnerId,
                m.player1_id AS player1Id,
                m.player2_id AS player2Id,
                m.start_time AS startTime,
                m.end_time AS endTime,
                d.points,
                d.is_quit AS isQuit,
                d.result,
                CASE
                    WHEN d.player_id = m.player1_id THEN p2.username
                    ELSE p1.username
                END AS opponentName
            FROM tbldetailmatch d
            JOIN tblmatch m ON d.match_id = m.id
            JOIN tblplayer p1 ON m.player1_id = p1.id
            JOIN tblplayer p2 ON m.player2_id = p2.id
            WHERE d.player_id = ?
            ORDER BY m.start_time ASC 
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Match match = new Match();
                    match.setId(rs.getInt("matchId"));
                    match.setTheme(rs.getString("theme"));
                    match.setWinnerId(rs.getInt("winnerId"));
                    match.setPlayer1Id(rs.getInt("player1Id"));
                    match.setPlayer2Id(rs.getInt("player2Id"));

                    String matchStatus = rs.getString("matchStatus");
                    if (matchStatus != null && !matchStatus.isEmpty()) {
                        match.setStatus(MatchStatus.valueOf(matchStatus.toUpperCase()));
                    }

                    Timestamp startTs = rs.getTimestamp("startTime");
                    Timestamp endTs = rs.getTimestamp("endTime");
                    if (startTs != null)
                        match.setStartTime(startTs.toLocalDateTime());
                    if (endTs != null)
                        match.setEndTime(endTs.toLocalDateTime());

                    // Opponent
                    Player opponent = new Player();
                    opponent.setUsername(rs.getString("opponentName"));

                    // DetailMatch
                    DetailMatch detail = new DetailMatch();
                    detail.setMatch(match);
                    detail.setPlayer(opponent);
                    detail.setPoints(rs.getInt("points"));
                    detail.setQuit(rs.getBoolean("isQuit"));

                    String resultStr = rs.getString("result");
                    if (resultStr != null && !resultStr.isEmpty()) {
                        detail.setResult(DetailMatchResult.valueOf(resultStr.toUpperCase()));
                    }

                    historyList.add(detail);
                }
            }

            System.out.println("[Server] Truy vấn chi tiết lịch sử cho player ID " + playerId +
                    " thành công. Tổng số bản ghi: " + historyList.size());

        } catch (SQLException e) {
            System.out.println("[Server] Lỗi khi truy vấn lịch sử chi tiết cho player ID: " + playerId);
            e.printStackTrace();
        }
        return historyList;
    }

    //Cap nhat nguoi chien thang sau tran dau
    public void updateMatchWinner(Match match) {
        String sql = "UPDATE tblmatch SET winner_id=?, status = ?, end_time = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (match.getWinnerId() > 0) {
                ps.setInt(1, match.getWinnerId());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, MatchStatus.FINISHED.toString());
            ps.setString(3, LocalDateTime.now().toString());
            ps.setInt(4, match.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Match ID " + match.getId() + " updated. Winner ID: " + match.getWinnerId());
            } else {
                System.out.println("⚠️ Match ID " + match.getId() + " not found or not updated.");
            }
        } catch (SQLException e) {
            System.out.println("Loi cap nhat Match ID: " +  match.getId());
            e.printStackTrace();
        }
    }

    //Cap nhat trang thai tran dau
    public boolean updateMatchStatus(int matchId, MatchStatus newStatus) throws SQLException {
        String sql = "UPDATE tblmatch SET status = ? WHERE id = ?";
        int rowsAffected = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // 2. Gán các giá trị
            ps.setString(1, newStatus.toString()); // Chuyển enum thành String (vd: "PLAYING")
            ps.setInt(2, matchId);                 // Gán ID cho điều kiện WHERE

            // 3. Thực thi
            rowsAffected = ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái trận đấu ID " + matchId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return rowsAffected > 0;
    }
}
