package server.controller;

import constants.DetailMatchResult;
import constants.MatchStatus;
import constants.MessageType;
import entity.DetailMatch;
import entity.Match;
import entity.Message;
import entity.Player;
import server.dao.DetailMatchDAO;
import server.dao.MatchDAO;
import server.dao.PlayerDAO;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameRoom {

    // Dinh nghia hang so
    private enum GameState { WAITING_FOR_START, COUNTDOWN, PLAYING, ENDED }
    private static final int GRID_ROWS = 4;
    private static final int GRID_COLS = 6;
    private static final int MAX_PAIRS = (GRID_ROWS * GRID_COLS) / 2;
    private static final int TURN_TIMEOUT_SECONDS = 15;
    private static final int MATCH_TIMEOUT_MINUTES = 15;
    private static final int COUNTDOWN_SECONDS = 3;
    private static final int CARD_SHOW_MS = 300;
    private static final long CARD_MISMATCH_DELAY_MS = 5;
    private static final long CARD_MATCH_DELAY_MS = 5;

    //DAO
    private final MatchDAO matchDAO;
    private final PlayerDAO playerDAO;
    private final DetailMatchDAO detailMatchDAO;

    //Players
    private final ClientHandler player1Handler;
    private final ClientHandler player2Handler;
    private Match match;
    private int player1Pairs;
    private int player2Pairs;
    private ClientHandler currentTurnHandler;
    private final String selectedTheme;

    //Xy ly trong Game
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Object gameLock = new Object(); // Lock để đồng bộ hóa trạng thái game (GameState)
    private volatile GameState gameState = GameState.WAITING_FOR_START;
    private int[][] matrix;
    private Map<Integer, String> imageCache;
    private Set<Integer> matchedCardIndices = new HashSet<>();
    private ScheduledFuture<?> turnTimeoutTask;
    private ScheduledFuture<?> matchTimeoutTask;
    private ScheduledFuture<?> cleanupTask;
    private Boolean player1WantsRematch = false;
    private Boolean player2WantsRematch = false;


    // Dem so lan timeout lien tiep
    private int player1ConsecutiveTimeouts = 0;
    private int player2ConsecutiveTimeouts = 0;

    // Mau sac co dinh cho 2 nguoi choi
    private final String player1Color = "RED";
    private final String player2Color = "BLUE";

    //Flag
    private volatile boolean isProcessingMove = false;
    private String endReason;

    public GameRoom(ClientHandler player1, ClientHandler player2, PlayerDAO playerDAO, MatchDAO matchDAO, DetailMatchDAO detailMatchDAO, String theme) {
        this.player1Handler = player1;
        this.player2Handler = player2;
        this.playerDAO = playerDAO;
        this.matchDAO = matchDAO;
        this.detailMatchDAO = detailMatchDAO;
        this.selectedTheme = theme;
        //Khi khởi tạo game ở InviteRespone nó sẽ gọi hàm này để chuẩn bị đếm nguọc bắt đầu trận đấu
        startCountdown();
    }

    private void startCountdown() {
        synchronized (gameLock) {
            if (gameState != GameState.WAITING_FOR_START &&  gameState != GameState.ENDED) return;
            this.player1WantsRematch = false;
            this.player2WantsRematch = false;
            gameState = GameState.COUNTDOWN;
        }
        for (int i = COUNTDOWN_SECONDS; i >= 1; i--) {
            final int count = i;
            Object[] countdownData = {count, "Bat dau sau ..."};

            long delaySeconds = COUNTDOWN_SECONDS - i;
            scheduler.schedule(() -> {
                try {
                    sendToBoth(new Message(MessageType.GAME_COUNTDOWN, countdownData));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, delaySeconds, TimeUnit.SECONDS);
        }
        //goi ham startMatch de tien hanh bat dau tran dau
        scheduler.schedule(this::startMatch, COUNTDOWN_SECONDS, TimeUnit.SECONDS);
    }


    private void startMatch() {
        try {
            synchronized (gameLock) {
                if (gameState != GameState.COUNTDOWN) return;

                initializeNewMatchState();
                createMatrixAndCacheImages();
                match.setId(matchDAO.createMatch(match));

                Object[] matrixConfig = new Object[3];
                matrixConfig[0] = flattenMatrix();       // ma trận ảnh
                matrixConfig[1] = selectedTheme;         // Chủ đề
                matrixConfig[2] = imageCache;            //  ảnh

                int firstTurnPlayerId = currentTurnHandler.getPlayer().getId();

                Map<String, Object> gameData = new HashMap<>();
                gameData.put("matrixConfig", matrixConfig);
                gameData.put("player1", player1Handler.getPlayer());
                gameData.put("player2", player2Handler.getPlayer());
                gameData.put("firstTurnPlayerId", firstTurnPlayerId);
                gameData.put("theme", selectedTheme);

                sendToBoth(new Message(MessageType.START_GAME, gameData));

                gameState = GameState.PLAYING;
                requestNextTurn(currentTurnHandler);
            }

            System.out.println("🎮 Game started successfully with theme: " + selectedTheme);

        } catch (IOException | SQLException e) {
            e.printStackTrace();
            sendToBoth(new Message(MessageType.INVITE_ERROR, "Lỗi server, không thể bắt đầu trận đấu."));
            cleanupAndCloseRoom();
        }
    }

    public void handlePlayerDisconnect(ClientHandler disconnectedPlayer) {
        synchronized (gameLock) {
            if (gameState == GameState.ENDED) return;
            ClientHandler winner = (disconnectedPlayer == player1Handler) ? player2Handler : player1Handler;
            endMatch(winner, disconnectedPlayer, true);
        }
    }


    //Logic khi Click Card
    public void handlePlayerMove(int cardIndex1, int cardIndex2, ClientHandler player) {
        synchronized (gameLock) {
            if (gameState != GameState.PLAYING || player != currentTurnHandler || isProcessingMove) {
                return;
            }

            isProcessingMove = true;

            if (turnTimeoutTask != null) {
                turnTimeoutTask.cancel(true);
            }

            // Reset bộ đếm timeout liên tiếp khi người chơi thực sự lật bài
            if (player == player1Handler) {
                player1ConsecutiveTimeouts = 0;
            } else {
                player2ConsecutiveTimeouts = 0;
            }
        }

        int val1 = matrix[cardIndex1 / GRID_COLS][cardIndex1 % GRID_COLS];
        int val2 = matrix[cardIndex2 / GRID_COLS][cardIndex2 % GRID_COLS];
        boolean isMatch = (val1 == val2);

        Object[] moveData = {cardIndex1, cardIndex2, val1, val2};
        ClientHandler opponent = (player == player1Handler) ? player2Handler : player1Handler;
        opponent.sendMessage(new Message(MessageType.FLIP_CARD_RESULT, moveData));

        scheduler.schedule(() -> {
            if (isMatch) {
                processMatch(cardIndex1, cardIndex2, player);
            } else {
                processMismatch(cardIndex1, cardIndex2, player);
            }
        }, CARD_SHOW_MS, TimeUnit.MILLISECONDS);
    }
    private void processMatch(int idx1, int idx2, ClientHandler playerWhoScored) {
        synchronized (gameLock) {
            if (gameState != GameState.PLAYING) {
                isProcessingMove = false;
                return;
            }

            //tang deim cho nguoi choi
            if (playerWhoScored == player1Handler) {
                player1Pairs++;
            } else if (playerWhoScored == player2Handler){
                player2Pairs++;
            }

            matchedCardIndices.add(idx1);
            matchedCardIndices.add(idx2);

            Object[] matchData = new Object[6];
            matchData[0] = idx1;
            matchData[1] = idx2;
            matchData[2] = playerWhoScored.getPlayer();
            matchData[3] = player1Pairs;
            matchData[4] = player2Pairs;
            matchData[5] = (playerWhoScored == player1Handler) ? player1Color : player2Color;
            sendToBoth(new Message(MessageType.CARD_MATCHED_RESULT, matchData));

            if ((player1Pairs + player2Pairs) == MAX_PAIRS) {
                ClientHandler winner = (player1Pairs > player2Pairs) ? player1Handler : ((player2Pairs > player1Pairs) ? player2Handler : null);
                ClientHandler loser = (winner == null) ? null : (winner == player1Handler ? player2Handler : player1Handler);
                endMatch(winner, loser, false);
                isProcessingMove = false;
            } else {
                scheduler.schedule(() -> {
                    synchronized (gameLock) {
                        if (gameState != GameState.PLAYING) return;
                        requestNextTurn(playerWhoScored);
                        isProcessingMove = false;
                    }
                }, CARD_MATCH_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void processMismatch(int idx1, int idx2, ClientHandler playerWhoMoved) {

        ClientHandler nextPlayer = (playerWhoMoved == player1Handler) ? player2Handler : player1Handler;
        synchronized (gameLock) {
            if (gameState != GameState.PLAYING) {
                isProcessingMove = false;
                return;
            }

            Object[] mismatchData = new Object[2];
            mismatchData[0] = idx1;
            mismatchData[1] = idx2;
            sendToBoth(new Message(MessageType.CARD_NOT_MATCHED_RESULT, mismatchData));

            scheduler.schedule(() -> {
                synchronized (gameLock) {
                    if (gameState != GameState.PLAYING) return; // Kiểm tra lại
                    requestNextTurn(nextPlayer);
                    isProcessingMove = false;
                }
            }, CARD_MISMATCH_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void requestNextTurn(ClientHandler nextPlayer) {
        synchronized (gameLock) {

            currentTurnHandler = nextPlayer;
            if (gameState != GameState.PLAYING) return;

            Map<String, Object> turnData = Map.of("nextPlayerId", nextPlayer.getPlayer().getId(), "timeLimit", TURN_TIMEOUT_SECONDS);
            sendToBoth(new Message(MessageType.TURN_CHANGED, turnData));

            turnTimeoutTask = scheduler.schedule(() -> {
                handleTurnTimeout(nextPlayer);
            }, TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void handleTurnTimeout(ClientHandler playerWhoTimedOut) {
        synchronized (gameLock) {
            if (gameState != GameState.PLAYING) return;

            if (playerWhoTimedOut == player1Handler) {
                player1ConsecutiveTimeouts++;
            } else {
                player2ConsecutiveTimeouts++;
            }

            Map<String, Object> timeoutData = Map.of("player", currentTurnHandler.getPlayer().getUsername());
            sendToBoth(new Message(MessageType.TURN_TIMEOUT, timeoutData));

            if (playerWhoTimedOut == player1Handler && player1ConsecutiveTimeouts >= 3) {
                sendToBoth(new Message(MessageType.KICK_DUE_TO_TIMEOUT, player1Handler.getPlayer().getUsername()));
                endMatch(player2Handler, player1Handler, false);
                return;
            } else if (playerWhoTimedOut == player2Handler && player2ConsecutiveTimeouts >= 3) {
                sendToBoth(new Message(MessageType.KICK_DUE_TO_TIMEOUT, player2Handler.getPlayer().getUsername()));
                endMatch(player1Handler, player2Handler, false);
                return;
            }

            ClientHandler nextPlayer = (playerWhoTimedOut == player1Handler) ? player2Handler : player1Handler;
            requestNextTurn(nextPlayer);
        }
    }

    private void endMatch(ClientHandler winner, ClientHandler loser, boolean wasDisconnect) {
        // Kiem tra trang thai game va tien hanh don dep
        synchronized (gameLock) {
            if (gameState == GameState.ENDED) return;
            gameState = GameState.ENDED;
            if (turnTimeoutTask != null) turnTimeoutTask.cancel(true);
            if (matchTimeoutTask != null) matchTimeoutTask.cancel(true);

            int winnerId = (winner != null) ? winner.getPlayer().getId() : -1;

            // Cập nhật CSDL
            match.setWinnerId(winnerId);
            match.setStatus(MatchStatus.FINISHED);
            match.setEndTime(LocalDateTime.now());
            matchDAO.updateMatchWinner(match);

            //Luu vao DetailMatch
            try {
                if (winner != null && loser != null) {
                    double eloChangeWinner = 1.0;
                    double eloChangeLoser = 0.0;

                    playerDAO.updatePlayerElo(winner.getPlayer(), eloChangeWinner);
                    playerDAO.updatePlayerElo(loser.getPlayer(), eloChangeLoser);

                    //Tao DetailMatch cho nguoi thang
                    DetailMatch winnerDetail = new DetailMatch();
                    winnerDetail.setMatch(match);
                    winnerDetail.setPlayer(winner.getPlayer());
                    winnerDetail.setPoints(getScoreOf(winner));
                    winnerDetail.setResult(DetailMatchResult.WIN);
                    winnerDetail.setQuit(false);

                    //Tao DetailMatch cho nguoi thua
                    DetailMatch loserDetail = new DetailMatch();
                    loserDetail.setMatch(match);
                    loserDetail.setPlayer(loser.getPlayer());
                    loserDetail.setPoints(getScoreOf(loser));
                    loserDetail.setResult(DetailMatchResult.LOSE);
                    loserDetail.setQuit(wasDisconnect);

                    //Luu vao CSDL
                    detailMatchDAO.addDetailMatch(winnerDetail);
                    detailMatchDAO.addDetailMatch(loserDetail);
                } else if (winner == null && loser == null && !wasDisconnect) {
                    double eloChange = 0.5;

                    playerDAO.updatePlayerElo(player1Handler.getPlayer(), eloChange);
                    playerDAO.updatePlayerElo(player2Handler.getPlayer(), eloChange);

                    //Tao detail match cho Player1
                    DetailMatch player1Detail = new DetailMatch();
                    player1Detail.setMatch(match);
                    player1Detail.setPlayer(player1Handler.getPlayer());
                    player1Detail.setPoints(player1Pairs);
                    player1Detail.setResult(DetailMatchResult.DRAW);
                    player1Detail.setQuit(false);

                    //Tao detail match cho player2
                    DetailMatch player2Detail = new DetailMatch();
                    player2Detail.setMatch(match);
                    player2Detail.setPlayer(player2Handler.getPlayer());
                    player2Detail.setPoints(player2Pairs);
                    player2Detail.setResult(DetailMatchResult.DRAW);
                    player2Detail.setQuit(false);

                    //Luu vao CSDL
                    detailMatchDAO.addDetailMatch(player1Detail);
                    detailMatchDAO.addDetailMatch(player2Detail);
                }
            } catch (Exception e) {
                System.out.println("Khong the cap nhat Elo");
                e.printStackTrace();
            }

            // Gửi kết quả về cho client
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("winnerId", winnerId);
            resultData.put("player1FinalPairs", player1Pairs);
            resultData.put("player2FinalPairs", player2Pairs);
            sendToBoth(new Message(MessageType.GAME_END, resultData));

            // tre 5s de client nhan tin nhan
            this.cleanupTask = scheduler.schedule(this::cleanupAndCloseRoom, 5, TimeUnit.SECONDS);
        }
    }

    private int getScoreOf(ClientHandler winner) {
        if (winner == null) return 0;

        if (winner.equals(player1Handler)) {
            return player1Pairs;
        } else if (winner.equals(player2Handler)) {
            return player2Pairs;
        } else {
            return 0;
        }
    }

    private void cleanupAndCloseRoom() {
        try {
            // cap nhat Online sau khi xong game
            playerDAO.updatePlayerStatus(player1Handler.getPlayer().getId(), "ONLINE");
            playerDAO.updatePlayerStatus(player2Handler.getPlayer().getId(), "ONLINE");

            List<Player> onlineUsers = playerDAO.getAllPlayers();
            player1Handler.getServer().broadcast(new Message(MessageType.ONLINE_LIST_UPDATE, onlineUsers));

            player1Handler.clearGameRoom();
            player2Handler.clearGameRoom();
            if (gameState == GameState.ENDED && !player1WantsRematch && !player2WantsRematch) {
                if (!scheduler.isShutdown()) {
                    scheduler.shutdownNow();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // Khởi tạo trạng thái trận đấu
    private void initializeNewMatchState() {
        this.player1Pairs = 0;
        this.player2Pairs = 0;
        this.currentTurnHandler = new Random().nextBoolean() ? player1Handler : player2Handler;
        this.matchedCardIndices.clear();
        this.player1ConsecutiveTimeouts = 0;
        this.player2ConsecutiveTimeouts = 0;
        this.player1WantsRematch = false;
        this.player2WantsRematch = false;

        this.match = new Match();
        match.setPlayer1Id(player1Handler.getPlayer().getId());
        match.setPlayer2Id(player2Handler.getPlayer().getId());
        match.setStatus(MatchStatus.PLAYING);
        match.setStartTime(LocalDateTime.now());
        match.setTheme(selectedTheme);
    }

    //Ham xu ly sinh anh
    private void createMatrixAndCacheImages() throws IOException {
        this.imageCache = new HashMap<>();
        // Load ảnh và mã hóa Base64
        for (int id = 0; id < MAX_PAIRS; id++) {
            String fileName = String.format("img%02d.jpg", id + 1);
            String resourcePath = "/assets/images/" + selectedTheme + "/" + fileName;
            byte[] bytes;

            try (var inputStream = GameRoom.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    // Check lỗi nếu khônng load được ảnh
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

        // Đổ vào ma trận
        this.matrix = new int[GRID_ROWS][GRID_COLS];

        for (int i = 0; i < ids.size(); i++) {
            matrix[i / GRID_COLS][i % GRID_COLS] = ids.get(i);
        }
        //In ma tran test cho nhanh
        for (int i = 0; i < GRID_ROWS; i++) {
            for (int j = 0; j < GRID_COLS; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }

    private int[] flattenMatrix() {
        return Arrays.stream(matrix).flatMapToInt(Arrays::stream).toArray();
    }

    public synchronized void playerWantsReplay(ClientHandler handler) throws IOException {
        if(gameState != GameState.ENDED) {
            return;
        }
        if(this.cleanupTask != null) {
            this.cleanupTask.cancel(false);
        }

        if (handler == player1Handler) player1WantsRematch = true;
        if (handler == player2Handler) player2WantsRematch = true;

        System.out.println("Player 1 want replay " + player1WantsRematch);
        System.out.println("Player 2 want replay " + player2WantsRematch);

        if (player1WantsRematch && player2WantsRematch) {
            startCountdown();
        }
    }

    public synchronized void playerRejectsReplay(ClientHandler handler) {
        if (gameState != GameState.ENDED) return;
        if (this.cleanupTask != null) {
            this.cleanupTask.cancel(false);
        }
        sendToBoth(new Message(MessageType.RETURN_TO_LOBBY, "Đối thủ đã từ chối chơi lại."));
        cleanupAndCloseRoom();
    }

    private void restartGame() throws IOException {
        player1WantsRematch = false;
        player2WantsRematch = false;

        System.out.println("Cả hai đã đồng ý, bắt đầu lại trận!");
//        sendToBoth(new Message(MessageType.START_GAME,));
        startCountdown();
    }

    private void sendToBoth(Message message) {
        player1Handler.sendMessage(message);
        player2Handler.sendMessage(message);
    }
}

