package constants;

public enum MessageType {
    //Authentication
    LOGIN("login"),
    LOGIN_SUCCESS("login_success"),
    LOGIN_FAILURE("login_failure"),
    LOGOUT("logout"),
    LOGOUT_SUCCESS("logout_success"),
    SIGN_UP("sign_up"),
    SIGN_UP_FAILURE("sign_up_failure"),
    SIGN_UP_SUCCESS("sign_up_success"),
    STATUS_UPDATE("status_update"),

    //Ranking and History
    GET_RANKING("get_ranking"),
    GET_RANKING_SUCCESS("get_ranking_failure"),
    GET_HISTORY("get_history"),
    GET_HISTORY_SUCCESS("get_history_success"),
    GET_HISTORY_DETAIL("get_history_detail"),
    GET_HISTORY_DETAIL_SUCCESS("get_history_detail_success"),
    //Notification
    UPDATE_PLAYER_STATUS("update_player_status"),
    GET_PLAYERS("get_players"),
    PLAYER_LIST("player_list"),
    START_GAME("start_game"),
    INVITE_REQUEST("invite_request"), // from client to server: fromA -> server
    INVITE_INCOMING("invite_incoming"), // from server to client: server -> toB
    INVITE_ERROR("invite_error"), // from server to client, if the invited player is not available
    INVITE_RESPONSE("invite_response"), // from client to server: toB -> server
    INVITE_RESULT("invite_result"), // from server to client: server -> fromA
    //Connection
    CONNECTED("connected"),
    DISCONNECTED("disconnected"),
    ERROR("error"),
    //GET
    ONLINE_LIST("online_list"),

    //Logic_GameRoom
    ONLINE_LIST_UPDATE("ONLINE_LIST_UPDATE"), // Cap nhat List nguoi choi Online (Optional)
    GAME_COUNTDOWN("GAME_COUNTDOWN"), // Dam nguoc truoc khi vao tran 2 - 3s
    PLAYER_MOVE("PLAYER_MOVE"), // Khi nguoi choi clik vao the se goi va xu ly messgae nay
    CARD_MATCHED_RESULT("CARD_MATCHED_RESULT"), // Ket qua neu the khop
    CARD_NOT_MATCHED_RESULT("CARD_NOT_MATCHED_RESULT"), // Neu the khong khop
    TURN_CHANGED("TURN_CHANGED"),
    TURN_TIMEOUT("TURN_TIMEOUT"),
    CONTINUE_GAME("CONTINUE_GAME"),
    GAME_END("GAME_END"),
    QUIT_GAME("QUIT_GAME"),
    QUIT_GAME_SUCCESS("QUIT_GAME_SUCCESS"),
    FLIP_CARD_RESULT("FLIP_CARD_RESULT"),
    OPPONENT_WANTS_CONTINUE("OPPONENT_WANTS_CONTINUE"),
    OPPONENT_QUIT_GAME("OPPONENT_QUIT_GAME"),
    UPDATE_SCORE("UPDATE_SCORE"),
    PLAY_AGAIN_REQUEST("PLAY_AGAIN_REQUEST"),
    PLAY_AGAIN_RESPOND("PLAY_AGAIN_RESPOND"),
    MATCH_RESULT("MATCH_RESULT");

    /**
     *
     */
    private final String value;

    private MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

}