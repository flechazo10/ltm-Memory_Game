package server.room;

import constants.MatchStatus;
import constants.MessageType;
import entity.Match;
import entity.Message;
import server.controller.ClientHandler;
import server.dao.DetailMatchDAO;
import server.dao.MatchDAO;
import server.dao.PlayerDAO;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GameManager {

    public enum GameState { WAITING_FOR_START, COUNTDOWN, PLAYING, ENDED }

    private final CardManager cardManager;
    private final TurnEngine turnEngine;
    private final TimeController timeController;
    private final ResultEngine resultEngine;

    private final ClientHandler player1Handler;
    private final ClientHandler player2Handler;
    private Match match;
    private final String selectedTheme;

    private final Object gameLock = new Object();
    private volatile GameState gameState = GameState.WAITING_FOR_START;
    
    private Boolean player1WantsRematch = false;
    private Boolean player2WantsRematch = false;
    private MatchDAO matchDAO;

    public GameManager(ClientHandler p1, ClientHandler p2, String theme, PlayerDAO pDAO, MatchDAO mDAO, DetailMatchDAO dmDAO) {
        this.player1Handler = p1;
        this.player2Handler = p2;
        this.selectedTheme = theme;
        this.matchDAO = mDAO;

        this.cardManager = new CardManager(theme);
        this.timeController = new TimeController(this);
        this.turnEngine = new TurnEngine(this);
        this.resultEngine = new ResultEngine(this, pDAO, mDAO, dmDAO);
    }

    public void startCountdown() {
        synchronized (gameLock) {
            if (gameState != GameState.WAITING_FOR_START && gameState != GameState.ENDED) return;
            this.player1WantsRematch = false;
            this.player2WantsRematch = false;
            gameState = GameState.COUNTDOWN;
        }

        for (int i = 3; i >= 1; i--) {
            final int count = i;
            Object[] countdownData = {count, "Bat dau sau ..."};
            timeController.scheduleTask(() -> sendToBoth(new Message(MessageType.GAME_COUNTDOWN, countdownData)), 3 - i, TimeUnit.SECONDS);
        }
        timeController.scheduleTask(this::startMatch, 3, TimeUnit.SECONDS);
    }

    private void startMatch() {
        try {
            synchronized (gameLock) {
                if (gameState != GameState.COUNTDOWN) return;

                turnEngine.determineFirstTurn();
                cardManager.shuffle();

                this.match = new Match();
                match.setPlayer1Id(player1Handler.getPlayer().getId());
                match.setPlayer2Id(player2Handler.getPlayer().getId());
                match.setStatus(MatchStatus.PLAYING);
                match.setStartTime(LocalDateTime.now());
                match.setTheme(selectedTheme);
                match.setId(matchDAO.createMatch(match));

                Object[] matrixConfig = new Object[3];
                matrixConfig[0] = cardManager.getFlattenMatrix();
                matrixConfig[1] = selectedTheme;
                matrixConfig[2] = cardManager.getImageCache();

                Map<String, Object> gameData = new HashMap<>();
                gameData.put("matrixConfig", matrixConfig);
                gameData.put("player1", player1Handler.getPlayer());
                gameData.put("player2", player2Handler.getPlayer());
                gameData.put("firstTurnPlayerId", turnEngine.getCurrentTurnHandler().getPlayer().getId());
                gameData.put("theme", selectedTheme);

                sendToBoth(new Message(MessageType.START_GAME, gameData));

                gameState = GameState.PLAYING;
                turnEngine.requestNextTurn(turnEngine.getCurrentTurnHandler());
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            sendToBoth(new Message(MessageType.INVITE_ERROR, "Lỗi server, không thể bắt đầu trận đấu."));
            resultEngine.cleanupAndCloseRoom();
        }
    }

    public void handlePlayerMove(int cardIndex1, int cardIndex2, ClientHandler player) {
        turnEngine.processMove(cardIndex1, cardIndex2, player);
    }

    public void handlePlayerDisconnect(ClientHandler disconnectedPlayer) {
        synchronized (gameLock) {
            if (gameState == GameState.ENDED) return;
            ClientHandler winner = (disconnectedPlayer == player1Handler) ? player2Handler : player1Handler;
            resultEngine.endMatch(winner, disconnectedPlayer, true);
        }
    }
    
    public synchronized void playerWantsReplay(ClientHandler handler) {
        if(gameState != GameState.ENDED) return;
        timeController.cancelCleanup();

        if (handler == player1Handler) player1WantsRematch = true;
        if (handler == player2Handler) player2WantsRematch = true;

        if (player1WantsRematch && player2WantsRematch) startCountdown();
    }

    public synchronized void playerRejectsReplay(ClientHandler handler) {
        if (gameState != GameState.ENDED) return;
        timeController.cancelCleanup();
        sendToBoth(new Message(MessageType.RETURN_TO_LOBBY, "Đối thủ đã từ chối chơi lại."));
        resultEngine.cleanupAndCloseRoom();
    }

    public void sendToBoth(Message message) {
        player1Handler.sendMessage(message);
        player2Handler.sendMessage(message);
    }

    // Getters / Setters
    public ClientHandler getPlayer1Handler() { return player1Handler; }
    public ClientHandler getPlayer2Handler() { return player2Handler; }
    public Object getGameLock() { return gameLock; }
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState state) { this.gameState = state; }
    public Match getMatch() { return match; }
    public CardManager getCardManager() { return cardManager; }
    public TurnEngine getTurnEngine() { return turnEngine; }
    public TimeController getTimeController() { return timeController; }
    public ResultEngine getResultEngine() { return resultEngine; }
    public boolean isPlayer1WantsRematch() { return player1WantsRematch; }
    public boolean isPlayer2WantsRematch() { return player2WantsRematch; }
}