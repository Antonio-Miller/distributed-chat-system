package chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public final class Server {
    private Server() {
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server listening on port " + port);
            while (true) {
                Socket client = serverSocket.accept();
                Thread.ofVirtual().start(() -> handleClient(client));
            }
        }
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return 5000;
        }

        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            System.err.println("Invalid port: " + args[0] + ", using default 5000");
            return 5000;
        }
    }

    private static void handleClient(Socket client) {
        String clientName = client.getRemoteSocketAddress().toString();

        try (Socket socket = client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            writer.println("WELCOME to the chat server");
            System.out.println("Client connected: " + clientName);

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(clientName + " -> " + line);
                writer.println("ECHO " + line);
            }
        } catch (IOException exception) {
            System.out.println("Client disconnected: " + clientName);
        }
    }
}
