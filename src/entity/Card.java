package entity;

import java.io.Serializable;

public class Card implements Serializable {
    private int id;
    private String name;
    private String path;
    private boolean isFlipped;
    private boolean isMacthed;
    private int matchedByPlayerId;
    public Card(int id, boolean isFlipped, boolean isMacthed, String name, String path) {
        this.id = id;
        this.isFlipped = isFlipped;
        this.isMacthed = isMacthed;
        this.name = name;
        this.path = path;
        this.matchedByPlayerId = 0;
    }

    public int getMatchedByPlayerId() {
        return matchedByPlayerId;
    }

    public void setMatchedByPlayerId(int matchedByPlayerId) {
        this.matchedByPlayerId = matchedByPlayerId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isFlipped() {
        return isFlipped;
    }

    public void setFlipped(boolean flipped) {
        isFlipped = flipped;
    }

    public boolean isMacthed() {
        return isMacthed;
    }

    public void setMacthed(boolean macthed) {
        isMacthed = macthed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
