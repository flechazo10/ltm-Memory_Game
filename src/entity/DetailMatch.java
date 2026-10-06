/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package entity;

import constants.DetailMatchResult;

import java.io.Serializable;

/**
 *
 * @author dbao0
 */
public class DetailMatch implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private int points;
    private  boolean isQuit;
    private DetailMatchResult result;
    Player player;
    Match match;
    public DetailMatch() {
    }

    public DetailMatch(int id, boolean isQuit, Match match, Player player, int points, DetailMatchResult result) {
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

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public DetailMatchResult getResult() {
        return result;
    }

    public void setResult(DetailMatchResult result) {
        this.result = result;
    }
}
