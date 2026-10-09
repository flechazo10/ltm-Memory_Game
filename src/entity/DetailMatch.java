package entity;

import constants.DetailMatchResult;

import java.io.Serializable;

/**
 * Chi tiết kết quả của 1 người chơi trong 1 trận - ánh xạ bảng tbldetailmatch.
 * points dùng double: thắng 1, hòa 0.5, thua 0.
 */
public class DetailMatch implements Serializable {
    private static final long serialVersionUID = 2L;

    private int id;
    private double points;
    private boolean isQuit;
    private DetailMatchResult result;
    private Player player;
    private Match match;

    public DetailMatch() {
    }

    public DetailMatch(int id, boolean isQuit, Match match, Player player, double points, DetailMatchResult result) {
        this.id = id;
        this.isQuit = isQuit;
        this.match = match;
        this.player = player;
        this.points = points;
        this.result = result;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isQuit() {
        return isQuit;
    }

    public void setQuit(boolean quit) {
        isQuit = quit;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = points;
    }

    public DetailMatchResult getResult() {
        return result;
    }

    public void setResult(DetailMatchResult result) {
        this.result = result;
    }
}
