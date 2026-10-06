package server.controller;

import constants.MessageType;
import constants.Status;
import entity.DetailMatch;
import entity.Message;
import entity.Player;
import javafx.util.Pair;
import server.dao.MatchDAO;
import server.dao.PlayerDAO;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {

    private Socket socket;
    private InitServer server;
    private final PlayerDAO playerDAO;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Player player;
    private GameRoom gameRoom;
    private String selectedTheme;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket, InitServer server, PlayerDAO playerDAO) {
        this.socket = socket;
        this.server = server;
        this.playerDAO = playerDAO;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Player getPlayer() {
        return player;
    }

    public GameRoom getGameRoom() {
        return gameRoom;
    }

    public void setGameRoom(GameRoom gameRoom) {
        this.gameRoom = gameRoom;
    }

    public void clearGameRoom() {
        this.gameRoom = null;
    }

    @Override
    public void run() {
        try {
            System.out.println("ClientHandler is running on: " + socket.getInetAddress());
            while (isRunning) {
                Message message = (Message) in.readObject();
                if (message != null) {
                    handleMessage(message);
                }
            }
        } catch (IOException | ClassNotFoundException | SQLException e) {
            System.out.println("Kết nối với " + (player != null ? player.getUsername() : "client") + " bị ngắt.");
            isRunning = false;
            if (gameRoom != null) {
                gameRoom.handlePlayerDisconnect(this);
            }
        } finally {
            try {
                if (player != null) {
                    playerDAO.updatePlayerStatus(player.getId(), String.valueOf(Status.OFFLINE));
                    server.broadcast(new Message(MessageType.STATUS_UPDATE, player.getUsername() + " đã offline."));
                    server.removeClient(this);
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(Message message) throws IOException, SQLException {
        switch (message.getType()) {
            case MessageType.LOGIN:
                handleLogin(message);
                break;
            case MessageType.GET_PLAYERS:
                handleGetPlayers();
                break;
            case MessageType.LOGOUT:
                handleLogout();
                break;
            case MessageType.GET_RANKING:
                handleGetRanking(message);
                break;
            case MessageType.GET_HISTORY:
                handleGetHistory(message);
                break;
            case MessageType.GET_HISTORY_DETAIL:
                handleGetHistoryDetail(message);
                break;
            case MessageType.INVITE_REQUEST:
                handleInvitePlayer(message);
                break;
            case MessageType.INVITE_RESPONSE:
                handleInviteResponse(message);
                break;
            case MessageType.PLAYER_MOVE:
                handleFlipCard(message);
                break;
            case MessageType.SIGN_UP:
                handleSignUp(message);
                break;
            case MessageType.QUIT_GAME:
                handleExitGameRoom(message);
                break;
            case MessageType.PLAY_AGAIN_REQUEST:
                handlePlayAgainRequest(message);
                break;
            case MessageType.REJECT_PLAY_AGAIN:
                handleRejectPlayAgain(message);
                break;
        }
    }
    private void handleExitGameRoom(Message message) throws IOException, SQLException {
        Object[] content = (Object[]) message.getContent();
        Player player = (Player) content[0];
        Player opponent = (Player) content[1];
        // xu li remove
        // TODO
        // xu li gui ve client
        System.out.println("Player " + player.getUsername() + " đã thoát phòng chơi.");
        System.out.println("Thông báo cho đối thủ " + opponent.getUsername() + " biết.");
        ClientHandler clientA = server.getClientHandler(player.getId());
        ClientHandler clientB = server.getClientHandler(opponent.getId());

        GameRoom gameRoom = server.getClientHandler(player.getId()).getGameRoom();
        if (gameRoom != null) {
            gameRoom.handlePlayerDisconnect(clientA);
        } else {
            System.out.println("Khong tim thay GameRoom");
        }

        PlayerDAO playerDAO =  new PlayerDAO();
        playerDAO.updatePlayerStatus(player.getId(), String.valueOf(Status.ONLINE));

        // gui message ve client
        clientA.sendMessage(new Message(MessageType.QUIT_GAME_SUCCESS, "Bạn đã thoát phòng chơi."));
        clientB.sendMessage(new Message(MessageType.QUIT_GAME_SUCCESS, "Đối thủ đã rời phòng chơi."));
    }

    private void handleSignUp(Message message) throws IOException, SQLException {
        String[] credentials = (String[]) message.getContent();
        String username = credentials[0];
        String password = credentials[1];

        try {
            boolean success = playerDAO.createPlayer(username, password);

            if (success) {
                sendMessage(new Message(MessageType.SIGN_UP_SUCCESS, "Sign up success!"));

            } else {
                sendMessage(new Message(MessageType.SIGN_UP_FAILURE, "Sign up failed! Try again."));
            }
        } catch (SQLException e) {
            sendMessage(new Message(MessageType.SIGN_UP_FAILURE, "Sign up failed!"));
            e.printStackTrace();
        }
    }

    private void handleLogin(Message message) throws IOException, SQLException {

        String[] credentials = (String[]) message.getContent();
        String username = credentials[0];
        String password = credentials[1];
        Pair<Player, Boolean> pairAuthenticatedUser = playerDAO.authenticate(username, password);
        Player _player = pairAuthenticatedUser.getKey();
        Boolean isOffline = pairAuthenticatedUser.getValue();
        if (_player != null && isOffline) {
            this.player = _player;
            System.out.println(player.getUsername() + player.getId());
            playerDAO.updatePlayerStatus(player.getId(), String.valueOf(Status.ONLINE));
            player.setStatus(String.valueOf(Status.ONLINE));
            sendMessage(new Message(MessageType.LOGIN_SUCCESS, player));
            server.broadcast(new Message(MessageType.STATUS_UPDATE, player.getUsername() + " is online."));
            server.addClient(player.getId(), this);

        } else if (_player != null) {
            sendMessage(new Message(MessageType.LOGIN_FAILURE, "Account is already logged in on another device."));
        } else {
            sendMessage(new Message(MessageType.LOGIN_FAILURE, "Please check your username and password."));
        }
    }

    private void handleLogout() throws IOException, SQLException {
        if (player != null) {
            playerDAO.updatePlayerStatus(player.getId(), String.valueOf(Status.OFFLINE));
            player.setStatus(String.valueOf(Status.OFFLINE));
            server.broadcast(new Message(MessageType.STATUS_UPDATE, player.getUsername() + " đã offline."));
            if (socket != null && !socket.isClosed()) {
                sendMessage(new Message(MessageType.LOGOUT_SUCCESS, "Đăng xuất thành công."));
            }
            server.removeClient(this);
        }
    }

    private void handleGetPlayers() throws IOException, SQLException {
        List<Player> allPlayers = playerDAO.getAllPlayers();
        List<Player> onlinePlayers = new ArrayList<>();
        for (Player p : allPlayers) {
            if (p.getStatus() != null &&
                (p.getStatus().equalsIgnoreCase(Status.ONLINE.getValue()) ||
                 p.getStatus().equalsIgnoreCase(Status.PLAYING.getValue()))) {
                onlinePlayers.add(p);
            }
        }
        sendMessage(new Message(MessageType.GET_PLAYERS, onlinePlayers));
    }
    //   Xử lý khi client yêu cầu GET_RANKING
    private void handleGetRanking(Message message) throws IOException, SQLException {
        List<Player> rankingList = playerDAO.getRankingList();

        List<Player> deepCopy = new ArrayList<>();
        for (Player p : rankingList) {
            Player clone = new Player();
            clone.setId(p.getId());
            clone.setUsername(p.getUsername());
            clone.setPassword(p.getPassword());
            clone.setStatus(p.getStatus());
            clone.setTotalScore(p.getTotalScore());
            clone.setRank(p.getRank());
            deepCopy.add(clone);
        }
        sendMessage(new Message(MessageType.GET_RANKING_SUCCESS, deepCopy));
        System.out.println(" [SERVER] Đã gửi danh sách ranking cho client");
    }

    private void handleGetHistory(Message message) throws IOException, SQLException {
        List<Player> historyList = playerDAO.getHistoryList();

        // mỗi Player là object mới hoàn toàn
        List<Player> deepCopy = new ArrayList<>();
        for (Player p : historyList) {
            Player clone = new Player();
            clone.setId(p.getId());
            clone.setUsername(p.getUsername());
            clone.setPassword(p.getPassword());
            clone.setStatus(p.getStatus());
            clone.setTotalScore(p.getTotalScore());
            clone.setRank(p.getRank());
            deepCopy.add(clone);
        }

        sendMessage(new Message(MessageType.GET_HISTORY_SUCCESS, deepCopy));
        System.out.println(" [SERVER] Đã gửi danh sách history cho client");
    }

    private void handleGetHistoryDetail(Message message) throws IOException, SQLException {

        int playerId = (Integer) message.getContent();

        MatchDAO matchDAO = new MatchDAO();
        List<DetailMatch> historyDetail = matchDAO.getHistoryDetailByPlayerId(playerId);

        System.out.println("[SERVER] Truy vấn xong: " + historyDetail.size() + " bản ghi");
        Object[] detail =  {historyDetail};
        sendMessage(new Message(MessageType.GET_HISTORY_DETAIL_SUCCESS, detail));
        System.out.println("[SERVER] ✅ Đã gửi danh sách chi tiết cho client");
    }

    private void handleInvitePlayer(Message message) throws IOException {
        Object[] content = (Object[]) message.getContent();
        Player fromPlayer = (Player) content[0];
        Player toPlayer = (Player) content[1];
        String selectedTheme = (String) content[2];

        ClientHandler fromClient = server.getClientHandler(fromPlayer.getId());
        ClientHandler toClient = server.getClientHandler(toPlayer.getId());

        //Set themes
        if (fromClient != null) {
            fromClient.setSelectedTheme(selectedTheme);
        }

        if (toClient != null) {
            System.out.println("HANDLER TO PLAYER: @" + toClient.hashCode());

            if (this.hashCode() == toClient.hashCode()) {
                System.out.println("ERROR: HANDLER FROM = HANDLER TO");
            } else {
                System.out.println("STATUS: OK! SENDING...");
            }

            Object[] sendContent = { fromPlayer, selectedTheme };
            // Gửi tin nhắn đi
            toClient.sendMessage(new Message(MessageType.INVITE_INCOMING, sendContent));
            System.out.println("Inviation has been sent to: " + toPlayer.getUsername());

        } else {
            System.out.println("!!!! LỖI: KHÔNG TÌM THẤY CLIENT HANDLER cho ID " + toPlayer.getId() + " !!!!");
            sendMessage(new Message(MessageType.INVITE_ERROR, "Người chơi không khả dụng"));
        }
        System.out.println("---[ KẾT THÚC DEBUG LỜI MỜI ]---\n");
    }

    private void handleInviteResponse(Message message) throws SQLException {
        // Lay thong tin tu tin nhan
        Object[] content = (Object[]) message.getContent();
        Player player_receive_request = (Player) content[0]; // người nhận lời mời (2)
        Player player_send_request = (Player) content[1]; // người gửi lời mời (1)
        boolean accepted = (boolean) content[2];
        String theme = (String) content[3];

        // Tim ClientHanlder cua nguoi moi (1)
        ClientHandler client_send_request = server.getClientHandler(player_send_request.getId());
        ClientHandler client_receive_request = this;
        if (accepted) {
            Object[] sendContent = {player_receive_request, player_send_request, true, theme};
            client_send_request.sendMessage(new Message(MessageType.INVITE_RESULT, sendContent));
            client_receive_request.sendMessage(new Message(MessageType.INVITE_RESULT, sendContent));

            //Khoi tao tran dau
            GameRoom room = new GameRoom(
                    client_send_request,
                    client_receive_request,
                    server.getPlayerDAO(),
                    server.getMatchDAO(),
                    server.getDetailMatchDAO(),
                    theme
            );

            this.gameRoom = room; // luu lai game room

            // gan phong va cap nhat trang thai
            client_send_request.setGameRoom(room);
            client_receive_request.setGameRoom(room);

            client_send_request.getPlayer().setStatus(String.valueOf(Status.PLAYING));
            client_receive_request.getPlayer().setStatus(String.valueOf(Status.PLAYING));

            server.getPlayerDAO().updatePlayerStatus(client_send_request.getPlayer().getId(), String.valueOf(Status.PLAYING));
            server.getPlayerDAO().updatePlayerStatus(client_receive_request.getPlayer().getId(), String.valueOf(Status.PLAYING));

            // lay danh sach nguoi online tu CSDL
            List<Player> onlinePlayers = server.getPlayerDAO().getAllPlayers();
            Message updateMsg = new Message(MessageType.ONLINE_LIST_UPDATE, onlinePlayers);

            server.broadcast(updateMsg);
        } else {
            // Nếu từ chối, chỉ cần báo cho người mời (A)
            System.out.println("SERVER: Invite from " + client_send_request.getPlayer().getUsername() + " was declined by " + client_receive_request.getPlayer().getUsername());
            Object[] result_content = {player_receive_request, player_send_request, false, theme};
            Message result_msg = new Message(MessageType.INVITE_RESULT, result_content);
            client_send_request.sendMessage(result_msg);
        }
    }

    // Logic Game
    private void handleFlipCard(Message message) throws SQLException {
        Object[] content = (Object[]) message.getContent();
        if (content == null || content.length < 3) {
            sendMessage(new Message(MessageType.ERROR, "Du lieu the khong hop le"));
            return;
        }

        int idx1 = (int) content[0];
        int idx2 = (int) content[1];
        Player p2 = (Player) content[2];

        //Gui message ca 2 deu thay the duoc lat
//        Object[] flipContent = {idx1, idx2, this.getPlayer().getUsername()};
//        ClientHandler p2Client =  server.getClientHandler(p2.getId());
//        p2Client.sendMessage(new Message(MessageType.FLIP_CARD_RESULT, flipContent));

        gameRoom.handlePlayerMove(idx1, idx2, this);
    }

    //ham gui tin nhan
    public void sendMessage(Message message) {
        try {
            if (socket != null && !socket.isClosed()) {
                out.writeObject(message);
                out.flush();
            } else {
                System.out.println("Socket đã đóng, không thể gửi tin nhắn tới "
                        + (player != null ? player.getUsername() : "client"));
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi gửi tin nhắn tới " + (player != null ? player.getUsername() : "client") + ": "
                    + e.getMessage());
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handlePlayAgainRequest(Message message) throws IOException {
        Player requester = (Player) message.getContent();
        ClientHandler client_requester = server.getClientHandler(requester.getId());
        if (gameRoom != null) {
            gameRoom.playerWantsReplay(client_requester);
        } else {
            sendMessage(new Message(MessageType.ERROR, "Không tìm thấy phòng chơi."));
        }
    }


    private void handleRejectPlayAgain(Message message) {
        if (gameRoom != null) {
            gameRoom.playerRejectsReplay(this);
        }
    }

    public boolean isDisconnected() {
        return socket == null || socket.isClosed();
    }

    //getter and setter
    public String getSelectedTheme() {
        return selectedTheme;
    }

    public void setSelectedTheme(String selectedTheme) {
        this.selectedTheme = selectedTheme;
    }

    public InitServer getServer() {
        return server;
    }

}