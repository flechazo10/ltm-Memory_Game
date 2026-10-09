package entity;

import java.io.Serializable;

/**
 * Thực thể người chơi - ánh xạ bảng tblplayer.
 * totalScore dùng double để lưu được điểm hòa (+0.5).
 * winCount dùng để xếp hạng phụ khi bằng điểm.
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 2L;

    private int id;
    private String username;
    private String password;
    private String status;
    private double totalScore;
    private int winCount;
    private int rank;

    public Player() {
    }

    public Player(int id, String username, String password, double totalScore, String status) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.totalScore = totalScore;
        this.status = status;
    }

    public Player(int id, String username, String password, double totalScore, int winCount, String status) {
        this(id, username, password, totalScore, status);
        this.winCount = winCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public int getWinCount() {
        return winCount;
    }

    public void setWinCount(int winCount) {
        this.winCount = winCount;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    @Override
    public String toString() {
        return "Player{id=" + id + ", username='" + username + "', status='" + status
                + "', totalScore=" + totalScore + ", winCount=" + winCount + "}";
    }
}
