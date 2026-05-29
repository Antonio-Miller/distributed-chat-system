package chat.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Basic integration test for the chat system.
 * Run the server first: java -cp out chat.server.ChatServer 12345
 */
public class ChatSystemTest {
    static class TestClient {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        TestClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String cmd) throws IOException {
            writer.write(cmd);
            writer.newLine();
            writer.flush();
        }

        String recv() throws IOException {
            return reader.readLine();
        }

        void close() throws IOException {
            socket.close();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String host = "localhost";
        int port = 12345;

        System.out.println("=== Chat System Integration Test ===");
        System.out.println("Connecting to " + host + ":" + port);

        // Test 1: Connection
        System.out.println("\n[Test 1] Connection");
        TestClient client1 = new TestClient(host, port);
        String greeting = client1.recv();
        System.out.println("  Received: " + greeting);
        assert greeting.startsWith("OK"), "Expected OK greeting";
        System.out.println("  ✓ Connection successful");

        // Test 2: Registration
        System.out.println("\n[Test 2] User Registration");
        client1.send("REGISTER testuser testpass123");
        String regResp = client1.recv();
        System.out.println("  Response: " + regResp);
        assert regResp.startsWith("OK"), "Expected OK for registration";
        String token = null;
        for (int i = 0; i < 5; i++) {
            String line = client1.recv();
            if (line.startsWith("TOKEN ")) {
                token = line.substring(6);
                System.out.println("  Token: " + token.substring(0, Math.min(20, token.length())) + "...");
            }
        }
        assert token != null, "Expected token after registration";
        System.out.println("  ✓ Registration successful");

        // Test 3: Room listing
        System.out.println("\n[Test 3] List Rooms");
        client1.send("ROOMS");
        String line;
        int roomCount = 0;
        while ((line = client1.recv()) != null) {
            if (line.startsWith("ROOM ")) roomCount++;
            if (line.equals("END_ROOMS")) break;
        }
        System.out.println("  Found " + roomCount + " rooms");
        assert roomCount >= 2, "Expected at least 2 default rooms";
        System.out.println("  ✓ Room listing works");

        // Test 4: Join room
        System.out.println("\n[Test 4] Join Room");
        client1.send("JOIN General");
        line = client1.recv();
        assert line != null && (line.startsWith("OK") || line.startsWith("JOINED")), "Expected JOINED response";
        System.out.println("  Response: " + line);
        System.out.println("  ✓ Join room works");

        // Test 5: Post message
        System.out.println("\n[Test 5] Post Message");
        client1.send("MSG Hello from test!");
        Thread.sleep(100);
        System.out.println("  ✓ Message sent (async)");

        // Test 6: Reconnection with token
        System.out.println("\n[Test 6] Session Resume (Fault Tolerance)");
        client1.close();
        Thread.sleep(500);
        TestClient client2 = new TestClient(host, port);
        client2.recv(); // greeting
        client2.send("RESUME " + token);
        line = client2.recv();
        System.out.println("  Resume response: " + line);
        assert line != null && line.startsWith("TOKEN"), "Expected TOKEN after RESUME";
        System.out.println("  ✓ Session resume works");

        client2.close();

        System.out.println("\n=== All tests passed ✓ ===");
    }
}
