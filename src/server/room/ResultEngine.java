package server.room;

import constants.DetailMatchResult;
import constants.MatchStatus;
import constants.MessageType;
import entity.DetailMatch;
import entity.Message;
import entity.Player;
import server.controller.ClientHandler;
import server.dao.DetailMatchDAO;
import server.dao.MatchDAO;
import server.dao.PlayerDAO;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ResultEngine {

    private GameManager gameManager;
    private PlayerDAO playerDAO;
    private MatchDAO matchDAO;
    private DetailMatchDAO detailMatchDAO;

    public ResultEngine(GameManager gameManager, PlayerDAO pDAO, MatchDAO mDAO, DetailMatchDAO dmDAO) {
        this.gameManager = gameManager;
        this.playerDAO = pDAO;
        this.matchDAO = mDAO;
        this.detailMatchDAO = dmDAO;
    }

    public void endMatch(ClientHandler winner, ClientHandler loser, boolean wasDisconnect) {
        synchronized (gameManager.getGameLock()) {
            if (gameManager.getGameState() == GameManager.GameState.ENDED) return;
            gameManager.setGameState(GameManager.GameState.ENDED);
            gameManager.getTimeController().cancelTurnTimer();

            int winnerId = (winner != null) ? winner.getPlayer().getId() : -1;

            // Cập nhật CSDL
            gameManager.getMatch().setWinnerId(winnerId);
            gameManager.getMatch().setStatus(MatchStatus.FINISHED);
            gameManager.getMatch().setEndTime(LocalDateTime.now());
            matchDAO.updateMatchWinner(gameManager.getMatch());

            try {
                if (winner != null && loser != null) {
                    playerDAO.updatePlayerElo(winner.getPlayer(), 1.0);
                    playerDAO.updatePlayerElo(loser.getPlayer(), 0.0);

                    DetailMatch winnerDetail = new DetailMatch();
                    winnerDetail.setMatch(gameManager.getMatch());
                    winnerDetail.setPlayer(winner.getPlayer());
                    winnerDetail.setPoints(getScoreOf(winner));
                    winnerDetail.setResult(DetailMatchResult.WIN);
                    winnerDetail.setQuit(false);
                    detailMatchDAO.addDetailMatch(winnerDetail);

                    DetailMatch loserDetail = new DetailMatch();
                    loserDetail.setMatch(gameManager.getMatch());
                    loserDetail.setPlayer(loser.getPlayer());
                    loserDetail.setPoints(getScoreOf(loser));
                    loserDetail.setResult(DetailMatchResult.LOSE);
                    loserDetail.setQuit(wasDisconnect);
                    detailMatchDAO.addDetailMatch(loserDetail);

                } else if (winner == null && loser == null && !wasDisconnect) {
                    playerDAO.updatePlayerElo(gameManager.getPlayer1Handler().getPlayer(), 0.5);
                    playerDAO.updatePlayerElo(gameManager.getPlayer2Handler().getPlayer(), 0.5);

                    DetailMatch p1Detail = new DetailMatch();
                    p1Detail.setMatch(gameManager.getMatch());
                    p1Detail.setPlayer(gameManager.getPlayer1Handler().getPlayer());
                    p1Detail.setPoints(gameManager.getTurnEngine().getPlayer1Pairs());
                    p1Detail.setResult(DetailMatchResult.DRAW);
                    p1Detail.setQuit(false);
                    detailMatchDAO.addDetailMatch(p1Detail);

                    DetailMatch p2Detail = new DetailMatch();
                    p2Detail.setMatch(gameManager.getMatch());
                    p2Detail.setPlayer(gameManager.getPlayer2Handler().getPlayer());
                    p2Detail.setPoints(gameManager.getTurnEngine().getPlayer2Pairs());
                    p2Detail.setResult(DetailMatchResult.DRAW);
                    p2Detail.setQuit(false);
                    detailMatchDAO.addDetailMatch(p2Detail);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("winnerId", winnerId);
            resultData.put("player1FinalPairs", gameManager.getTurnEngine().getPlayer1Pairs());
            resultData.put("player2FinalPairs", gameManager.getTurnEngine().getPlayer2Pairs());
            gameManager.sendToBoth(new Message(MessageType.GAME_END, resultData));

            gameManager.getTimeController().scheduleCleanup(this::cleanupAndCloseRoom, 5, TimeUnit.SECONDS);
        }
    }

    private int getScoreOf(ClientHandler handler) {
        if (handler == null) return 0;
        if (handler.equals(gameManager.getPlayer1Handler())) return gameManager.getTurnEngine().getPlayer1Pairs();
        if (handler.equals(gameManager.getPlayer2Handler())) return gameManager.getTurnEngine().getPlayer2Pairs();
        return 0;
    }

    public void cleanupAndCloseRoom() {
        try {
            playerDAO.updatePlayerStatus(gameManager.getPlayer1Handler().getPlayer().getId(), "ONLINE");
            playerDAO.updatePlayerStatus(gameManager.getPlayer2Handler().getPlayer().getId(), "ONLINE");

            List<Player> onlineUsers = playerDAO.getAllPlayers();
            gameManager.getPlayer1Handler().getServer().broadcast(new Message(MessageType.ONLINE_LIST_UPDATE, onlineUsers));

            gameManager.getPlayer1Handler().clearGameRoom();
            gameManager.getPlayer2Handler().clearGameRoom();

            if (gameManager.getGameState() == GameManager.GameState.ENDED && !gameManager.isPlayer1WantsRematch() && !gameManager.isPlayer2WantsRematch()) {
                gameManager.getTimeController().shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}