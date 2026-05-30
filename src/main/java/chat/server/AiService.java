package chat.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

final class AiService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final URI endpoint;
    private final String model;
    private final boolean mock;

    AiService() {
        String url = getenvOrProperty("OLLAMA_URL", "ollama.url", "http://localhost:11434/api/generate");
        this.endpoint = URI.create(url);
        this.model = getenvOrProperty("OLLAMA_MODEL", "ollama.model", "llama3.2");
        String mockFlag = getenvOrProperty("MOCK_AI", "mock.ai", "");
        this.mock = mockFlag != null && !mockFlag.isBlank();
        System.out.println("AiService configured: endpoint=" + endpoint + " model=" + model);
    }

    String ask(String roomPrompt, List<String> timeline) {
        if (mock) {
            // Simple deterministic mock reply useful for testing when Ollama is unavailable
            return "[Mock Bot] I am running in MOCK_AI mode and cannot reach the real LLM.";
        }
        String prompt = buildPrompt(roomPrompt, timeline);
        String body = "{"
                + "\"model\":\"" + JsonUtil.escape(model) + "\","
                + "\"stream\":false,"
                + "\"prompt\":\"" + JsonUtil.escape(prompt) + "\""
                + "}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = response.body();
                String snippet = body == null ? "" : (body.length() > 200 ? body.substring(0, 200) + "..." : body);
                return "[Bot unavailable: Ollama returned HTTP " + response.statusCode() + ": " + snippet + "]";
            }
            String answer = JsonUtil.extractStringField(response.body(), "response");
            if (answer == null || answer.isBlank()) return "[Bot produced no response]";
            return answer.strip().replace('\n', ' ');
        } catch (IOException e) {
            return "[Bot unavailable: cannot connect to local Ollama at " + endpoint + "]";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[Bot interrupted]";
        } catch (RuntimeException e) {
            return "[Bot error: " + e.getMessage() + "]";
        }
    }

    private static String buildPrompt(String roomPrompt, List<String> timeline) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Bot, a helpful participant inside a TCP chat room.\n");
        sb.append("Room instruction/prompt: ").append(roomPrompt).append("\n\n");
        sb.append("Conversation timeline:\n");
        for (String line : timeline) sb.append(line).append('\n');
        sb.append("\nReply with one concise message as Bot. Do not repeat the whole conversation.");
        return sb.toString();
    }

    private static String getenvOrProperty(String envName, String propertyName, String fallback) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) return property;
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) return env;
        return fallback;
    }
}
