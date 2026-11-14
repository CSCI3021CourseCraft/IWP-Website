package coursecraft.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coursecraft.util.Config;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal client for Groq's OpenAI-compatible chat API (tool calling), using java.net.http.
 * Settings (config.properties key -> environment variable):
 *   llm.api.key -> LLM_API_KEY   (GROQ_API_KEY is also accepted)   required
 *   llm.model   -> LLM_MODEL     (GROQ_MODEL is also accepted)     default llama-3.3-70b-versatile
 *   llm.url     -> LLM_URL                                          default Groq chat completions URL
 * The key is read from the environment / config.properties only, never hard-coded.
 * Error messages thrown here are written for the end user: they never contain the key or the raw API body.
 */
public final class GroqClient {

    private static final String DEFAULT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private GroqClient() {
    }

    private static String apiKey() {
        String k = Config.get("llm.api.key");
        if (k == null) {
            k = System.getenv("GROQ_API_KEY");
        }
        return (k == null || k.trim().isEmpty()) ? null : k.trim();
    }

    public static boolean hasApiKey() {
        return apiKey() != null;
    }

    /**
     * Sends the conversation plus tool definitions and returns the model's reply message
     * (choices[0].message: has "content" and/or "tool_calls").
     */
    public static JsonObject chat(JsonArray messages, JsonArray tools) throws IOException, InterruptedException {
        String key = apiKey();
        if (key == null) {
            throw new IOException("LLM_API_KEY is not configured on the server");
        }
        String model = Config.get("llm.model");
        if (model == null) {
            model = System.getenv("GROQ_MODEL");
        }
        if (model == null || model.trim().isEmpty()) {
            model = DEFAULT_MODEL;
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model.trim());
        body.add("messages", messages);
        body.add("tools", tools);
        body.addProperty("tool_choice", "auto");
        body.addProperty("temperature", 0.2);

        HttpRequest request = HttpRequest.newBuilder(URI.create(Config.get("llm.url", DEFAULT_URL)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {                       // no network, DNS failure, timeout, bad llm.url ...
            System.err.println("LLM unreachable: " + e);
            throw new IOException("Could not reach the LLM service. Check the server's internet connection.");
        }
        int status = response.statusCode();
        if (status != 200) {
            String raw = response.body() == null ? "" : response.body();
            System.err.println("LLM error " + status + ": " + (raw.length() > 500 ? raw.substring(0, 500) : raw));
            if (status == 429) {
                throw new IOException("The LLM rate limit was hit. Wait a minute and try again.");
            }
            if (status == 401 || status == 403) {
                throw new IOException("The LLM rejected the API key. Check LLM_API_KEY on the server.");
            }
            if (status == 400 && raw.contains("tool_use_failed")) {
                throw new IOException("The model produced an invalid tool call. Try rephrasing your request.");
            }
            throw new IOException("The LLM request failed (HTTP " + status + ").");
        }

        try {
            JsonElement choices = JsonParser.parseString(response.body()).getAsJsonObject().get("choices");
            return choices.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("message");
        } catch (RuntimeException e) {
            throw new IOException("The LLM returned a response in an unexpected format.");
        }
    }
}
