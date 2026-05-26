package chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public final class ChatServer {
    private ChatServer() {}

    public static void main(String[] args) throws IOException {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 12345;
        Path usersFile = args.length >= 2 ? Path.of(args[1]) : Path.of("data", "users.db");
        ServerState state = new ServerState(usersFile);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("CPD Chat Server listening on port " + port);
            System.out.println("Users file: " + usersFile.toAbsolutePath());
            System.out.println("Default users: alice/1234, bob/1234, eve/1234");
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                try {
                    ClientConnection connection = new ClientConnection(socket, state);
                    Thread.startVirtualThread(connection);
                } catch (IOException e) {
                    socket.close();
                }
            }
        }
    }
}
