package chat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public final class Client {
    private Client() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = parsePort(args);

        try (Socket socket = new Socket(host, port);
             BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverWriter = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to " + host + ":" + port);
            System.out.println("Type a message and press Enter. Use /quit to exit.");
            Thread.ofVirtual().start(() -> printServerMessages(serverReader));

            System.out.print("> ");
            System.out.flush();
            String line;
            while ((line = consoleReader.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                serverWriter.println(line);
                System.out.print("> ");
                System.out.flush();
            }
        } catch (IOException exception) {
            System.err.println("Could not connect to " + host + ":" + port + " - " + exception.getMessage());
        }
    }

    private static int parsePort(String[] args) {
        if (args.length < 2) {
            return 5000;
        }

        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            System.err.println("Invalid port: " + args[1] + ", using default 5000");
            return 5000;
        }
    }

    private static void printServerMessages(BufferedReader serverReader) {
        try {
            String line;
            while ((line = serverReader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException exception) {
            System.out.println("Server connection closed.");
        }
    }
}
