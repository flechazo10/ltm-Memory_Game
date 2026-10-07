package server.room;

import constants.MessageType;
import entity.Message;
import server.controller.ClientHandler;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class TurnEngine {

    private GameManager gameManager;
    private ClientHandler currentTurnHandler;
    
    private int player1Pairs = 0;
    private int player2Pairs = 0;
    private volatile boolean isProcessingMove = false;

    private static final int CARD_SHOW_MS = 300;
    private static final long CARD_MATCH_DELAY_MS = 5;
    private static final long CARD_MISMATCH_DELAY_MS = 5;
    private static final int MAX_PAIRS = 12;

    public TurnEngine(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void determineFirstTurn() {
        this.currentTurnHandler = new Random().nextBoolean() ? gameManager.getPlayer1Handler() : gameManager.getPlayer2Handler();
        this.player1Pairs = 0;
        this.player2Pairs = 0;
    }

    public ClientHandler getCurrentTurnHandler() {
        return currentTurnHandler;
    }

    public void processMove(int idx1, int idx2, ClientHandler player) {
        synchronized (gameManager.getGameLock()) {
            if (gameManager.getGameState() != GameManager.GameState.PLAYING || player != currentTurnHandler || isProcessingMove) {
                return;
            }
            isProcessingMove = true;
            gameManager.getTimeController().cancelTurnTimer();
            gameManager.getTimeController().resetPenalty(player);
        }

        CardManager cm = gameManager.getCardManager();
        int val1 = cm.getCard(idx1).getId();
        int val2 = cm.getCard(idx2).getId();
        boolean isMatch = (val1 == val2);

        Object[] moveData = {idx1, idx2, val1, val2};
        ClientHandler opponent = (player == gameManager.getPlayer1Handler()) ? gameManager.getPlayer2Handler() : gameManager.getPlayer1Handler();
        opponent.sendMessage(new Message(MessageType.FLIP_CARD_RESULT, moveData));

        gameManager.getTimeController().scheduleTask(() -> {
            if (isMatch) processMatch(idx1, idx2, player);
            else processMismatch(idx1, idx2, player);
        }, CARD_SHOW_MS, TimeUnit.MILLISECONDS);
    }

    private void processMatch(int idx1, int idx2, ClientHandler playerWhoScored) {
        synchronized (gameManager.getGameLock()) {
            if (gameManager.getGameState() != GameManager.GameState.PLAYING) {
                isProcessingMove = false;
                return;
            }

            if (playerWhoScored == gameManager.getPlayer1Handler()) player1Pairs++;
            else player2Pairs++;

            gameManager.getCardManager().setMatched(idx1, playerWhoScored.getPlayer().getId());
            gameManager.getCardManager().setMatched(idx2, playerWhoScored.getPlayer().getId());

            Object[] matchData = new Object[6];
            matchData[0] = idx1;
            matchData[1] = idx2;
            matchData[2] = playerWhoScored.getPlayer();
            matchData[3] = player1Pairs;
            matchData[4] = player2Pairs;
            matchData[5] = (playerWhoScored == gameManager.getPlayer1Handler()) ? "RED" : "BLUE";
            gameManager.sendToBoth(new Message(MessageType.CARD_MATCHED_RESULT, matchData));

            if ((player1Pairs + player2Pairs) == MAX_PAIRS) {
                ClientHandler winner = (player1Pairs > player2Pairs) ? gameManager.getPlayer1Handler() : ((player2Pairs > player1Pairs) ? gameManager.getPlayer2Handler() : null);
                ClientHandler loser = (winner == null) ? null : (winner == gameManager.getPlayer1Handler() ? gameManager.getPlayer2Handler() : gameManager.getPlayer1Handler());
                gameManager.getResultEngine().endMatch(winner, loser, false);
                isProcessingMove = false;
            } else {
                gameManager.getTimeController().scheduleTask(() -> {
                    synchronized (gameManager.getGameLock()) {
                        if (gameManager.getGameState() != GameManager.GameState.PLAYING) return;
                        requestNextTurn(playerWhoScored);
                        isProcessingMove = false;
                    }
                }, CARD_MATCH_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void processMismatch(int idx1, int idx2, ClientHandler playerWhoMoved) {
        ClientHandler nextPlayer = (playerWhoMoved == gameManager.getPlayer1Handler()) ? gameManager.getPlayer2Handler() : gameManager.getPlayer1Handler();
        synchronized (gameManager.getGameLock()) {
            if (gameManager.getGameState() != GameManager.GameState.PLAYING) {
                isProcessingMove = false;
                return;
            }

            Object[] mismatchData = {idx1, idx2};
            gameManager.sendToBoth(new Message(MessageType.CARD_NOT_MATCHED_RESULT, mismatchData));

            gameManager.getTimeController().scheduleTask(() -> {
                synchronized (gameManager.getGameLock()) {
                    if (gameManager.getGameState() != GameManager.GameState.PLAYING) return;
                    requestNextTurn(nextPlayer);
                    isProcessingMove = false;
                }
            }, CARD_MISMATCH_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void requestNextTurn(ClientHandler nextPlayer) {
        synchronized (gameManager.getGameLock()) {
            currentTurnHandler = nextPlayer;
            if (gameManager.getGameState() != GameManager.GameState.PLAYING) return;

            Map<String, Object> turnData = Map.of("nextPlayerId", nextPlayer.getPlayer().getId(), "timeLimit", 15);
            gameManager.sendToBoth(new Message(MessageType.TURN_CHANGED, turnData));

            gameManager.getTimeController().startTurnTimer(nextPlayer);
        }
    }

    public int getPlayer1Pairs() { return player1Pairs; }
    public int getPlayer2Pairs() { return player2Pairs; }
}