package entity;

import java.io.Serializable;

/**
 * Một lá bài trên bàn 4x6. Không lưu DB, chỉ dùng trong trận và gửi qua Socket.
 * matchedByPlayerId = 0 nghĩa là chưa ai ăn; khác 0 thì UI tô viền Đỏ/Xanh theo người đó.
 */
public class Card implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String path;
    private boolean isFlipped;
    private boolean isMatched;
    private int matchedByPlayerId;

    public Card(int id, boolean isFlipped, boolean isMatched, String name, String path) {
        this.id = id;
        this.isFlipped = isFlipped;
        this.isMatched = isMatched;
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

    public boolean isMatched() {
        return isMatched;
    }

    public void setMatched(boolean matched) {
        isMatched = matched;
    }

    /** @deprecated tên cũ bị sai chính tả, giữ lại để code của thành viên khác không lỗi. Dùng isMatched(). */
    @Deprecated
    public boolean isMacthed() {
        return isMatched;
    }

    /** @deprecated tên cũ bị sai chính tả. Dùng setMatched(). */
    @Deprecated
    public void setMacthed(boolean matched) {
        isMatched = matched;
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
