package server.room;

import entity.Card;
import java.io.IOException;
import java.util.*;

public class CardManager {

    private static final int GRID_ROWS = 4;
    private static final int GRID_COLS = 6;
    private static final int TOTAL_CARDS = GRID_ROWS * GRID_COLS;
    private static final int MAX_PAIRS = TOTAL_CARDS / 2;

    private Card[] cards = new Card[TOTAL_CARDS];
    private String theme;
    private Map<Integer, String> imageCache;

    public CardManager(String theme) {
        this.theme = theme;
        this.imageCache = new HashMap<>();
    }

    public void shuffle() throws IOException {
        this.imageCache = new HashMap<>();
        
        // Load ảnh và mã hóa Base64
        for (int id = 0; id < MAX_PAIRS; id++) {
            String fileName = String.format("img%02d.jpg", id + 1);
            String resourcePath = "/assets/images/" + theme + "/" + fileName;
            byte[] bytes;

            try (var inputStream = CardManager.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new IOException("Không tìm thấy file tài nguyên: " + resourcePath);
                }
                bytes = inputStream.readAllBytes();
            }
            String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes);
            imageCache.put(id, base64Image);
        }

        // Tạo mảng chỉ số và xáo trộn
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < MAX_PAIRS; i++) {
            ids.add(i);
            ids.add(i);
        }
        Collections.shuffle(ids);

        // Khởi tạo mảng Card
        for (int i = 0; i < TOTAL_CARDS; i++) {
            int imageId = ids.get(i);
            String fileName = String.format("img%02d.jpg", imageId + 1);
            String path = "/assets/images/" + theme + "/" + fileName;
            cards[i] = new Card(imageId, false, false, fileName, path);
        }
    }

    // Helper: Trả về mảng int (phục vụ cho Client đang dùng ma trận int)
    public int[] getFlattenMatrix() {
        int[] matrix = new int[TOTAL_CARDS];
        for (int i = 0; i < TOTAL_CARDS; i++) {
            matrix[i] = cards[i].getId();
        }
        return matrix;
    }

    public Map<Integer, String> getImageCache() {
        return imageCache;
    }

    public Card getCard(int index) {
        if (index >= 0 && index < TOTAL_CARDS) return cards[index];
        return null;
    }

    public void setMatched(int index, int playerId) {
        if (index >= 0 && index < TOTAL_CARDS) {
            cards[index].setMacthed(true);
            cards[index].setMatchedByPlayerId(playerId);
        }
    }

    public boolean isAllMatched() {
        for (Card card : cards) {
            if (!card.isMacthed()) return false;
        }
        return true;
    }
}