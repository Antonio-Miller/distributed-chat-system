package chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public final class ChatServer {
    private ChatServer() {}

    public static void main(String[] args) throws Exception {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 12345;
        Path usersFile = args.length >= 2 ? Path.of(args[1]) : Path.of("data", "users.db");
        boolean useTls = args.length >= 3 && args[2].equalsIgnoreCase("--tls");
        Path keystorePath = Path.of("data", "chat-server.p12");
        
        ServerState state = new ServerState(usersFile);

        ServerSocket serverSocket = createServerSocket(port, keystorePath, useTls);

        try (serverSocket) {
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

    private static ServerSocket createServerSocket(int port, Path keystorePath, boolean useTls) throws Exception {
        if (useTls) {
            try {
                SSLServerSocketFactory factory = SslUtil.createServerSocketFactory(keystorePath, "12345678");
                SSLServerSocket sslSocket = (SSLServerSocket) factory.createServerSocket(port);
                sslSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                System.out.println("CPD Chat Server (TLS enabled) listening on port " + port);
                return sslSocket;
            } catch (Exception e) {
                System.err.println("Failed to initialize TLS: " + e.getMessage());
                System.err.println("Falling back to plain TCP. Note: This is insecure!");
            }
        } else {
            System.out.println("WARNING: Server running in plain TCP (no TLS). This is insecure!");
            System.out.println("Start with '--tls' flag to enable TLS encryption:");
            System.out.println("  java -cp out chat.server.ChatServer <port> data/users.db --tls");
        }
        return new ServerSocket(port);
    }
}
