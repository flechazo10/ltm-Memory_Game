package server.room;

import constants.MessageType;
import entity.Message;
import server.controller.ClientHandler;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimeController {

    private GameManager gameManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> turnTimeoutTask;
    private ScheduledFuture<?> cleanupTask;
    
    private final int TURN_TIMEOUT_SECONDS = 15;
    private int player1ConsecutiveTimeouts = 0;
    private int player2ConsecutiveTimeouts = 0;

    public TimeController(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void resetPenalty(ClientHandler player) {
        if (player == gameManager.getPlayer1Handler()) player1ConsecutiveTimeouts = 0;
        else player2ConsecutiveTimeouts = 0;
    }

    public void cancelTurnTimer() {
        if (turnTimeoutTask != null) {
            turnTimeoutTask.cancel(true);
        }
    }

    public void startTurnTimer(ClientHandler player) {
        cancelTurnTimer();
        turnTimeoutTask = scheduler.schedule(() -> {
            handleTurnTimeout(player);
        }, TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void handleTurnTimeout(ClientHandler playerWhoTimedOut) {
        synchronized (gameManager.getGameLock()) {
            if (gameManager.getGameState() != GameManager.GameState.PLAYING) return;

            if (playerWhoTimedOut == gameManager.getPlayer1Handler()) player1ConsecutiveTimeouts++;
            else player2ConsecutiveTimeouts++;

            Map<String, Object> timeoutData = Map.of("player", playerWhoTimedOut.getPlayer().getUsername());
            gameManager.sendToBoth(new Message(MessageType.TURN_TIMEOUT, timeoutData));

            if (playerWhoTimedOut == gameManager.getPlayer1Handler() && player1ConsecutiveTimeouts >= 3) {
                gameManager.sendToBoth(new Message(MessageType.KICK_DUE_TO_TIMEOUT, gameManager.getPlayer1Handler().getPlayer().getUsername()));
                gameManager.getResultEngine().endMatch(gameManager.getPlayer2Handler(), gameManager.getPlayer1Handler(), false);
                return;
            } else if (playerWhoTimedOut == gameManager.getPlayer2Handler() && player2ConsecutiveTimeouts >= 3) {
                gameManager.sendToBoth(new Message(MessageType.KICK_DUE_TO_TIMEOUT, gameManager.getPlayer2Handler().getPlayer().getUsername()));
                gameManager.getResultEngine().endMatch(gameManager.getPlayer1Handler(), gameManager.getPlayer2Handler(), false);
                return;
            }

            ClientHandler nextPlayer = (playerWhoTimedOut == gameManager.getPlayer1Handler()) ? gameManager.getPlayer2Handler() : gameManager.getPlayer1Handler();
            gameManager.getTurnEngine().requestNextTurn(nextPlayer);
        }
    }

    public void scheduleTask(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    public void scheduleCleanup(Runnable task, long delay, TimeUnit unit) {
        cleanupTask = scheduler.schedule(task, delay, unit);
    }

    public void cancelCleanup() {
        if (cleanupTask != null) cleanupTask.cancel(false);
    }

    public void shutdown() {
        if (!scheduler.isShutdown()) scheduler.shutdownNow();
    }
}