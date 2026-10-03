package server;

import server.controller.InitServer;

public class ServerRun {

    private static final int PORT = 23456;

    public static void main(String[] args) {
        InitServer server = new InitServer(PORT);
        server.start();
    }
}