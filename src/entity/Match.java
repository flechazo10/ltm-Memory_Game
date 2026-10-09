package entity;

import constants.MatchStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Thông tin tổng quan 1 trận đấu - ánh xạ bảng tblmatch.
 * timeLimit tính bằng giây (mặc định 15 phút = 900 giây).
 */
public class Match implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int DEFAULT_TIME_LIMIT = 15 * 60;

    private int id;
    private int player1Id;
    private int player2Id;
    private int winnerId;          // 0 = hòa / chưa có người thắng (NULL trong DB)
    private String theme;
    private MatchStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int timeLimit = DEFAULT_TIME_LIMIT;

    public Match() {
    }

    public Match(LocalDateTime endTime, int id, int player1Id, int player2Id, LocalDateTime startTime,
                 MatchStatus status, String theme, int winnerId) {
        this.endTime = endTime;
        this.id = id;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.startTime = startTime;
        this.status = status;
        this.theme = theme;
        this.winnerId = winnerId;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPlayer1Id() {
        return player1Id;
    }

    public void setPlayer1Id(int player1Id) {
        this.player1Id = player1Id;
    }

    public int getPlayer2Id() {
        return player2Id;
    }

    public void setPlayer2Id(int player2Id) {
        this.player2Id = player2Id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(int winnerId) {
        this.winnerId = winnerId;
    }
}
