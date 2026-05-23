package com.example.michiru.service;

import com.example.michiru.model.IRoadmapGenerator;
import com.example.michiru.model.RoadmapModifier;
import com.example.michiru.model.ServiceUnavailableException;
import com.example.michiru.model.StudentReadinessDTO;
import com.example.michiru.model.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Roadmap generator implementation backed by Groq's chat completions API.
 */
public class GroqRoadmapService implements IRoadmapGenerator {

    private static final String GROQ_API_URL =
            "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final double TEMPERATURE  = 0.3;

    private final HttpClient               httpClient;
    private final RoadmapPromptOrchestrator orchestrator;

    public GroqRoadmapService() {
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.orchestrator = new RoadmapPromptOrchestrator();
    }

    @Override
    public List<Task> generateRoadmap(StudentReadinessDTO readiness,
                                      List<RoadmapModifier> modifiers,
                                      String mentorNotes) {
        String apiKey = resolveApiKey();
        String userPrompt = orchestrator.buildUserPrompt(readiness, modifiers, mentorNotes);
        String requestBody = buildRequestBody(userPrompt);

        HttpResponse<String> response = sendRequest(apiKey, requestBody);

        if (response.statusCode() != 200) {
            throw new ServiceUnavailableException(
                    "Groq API returned HTTP " + response.statusCode()
                            + ": " + response.body(),
                    response.statusCode());
        }

        return parseTasksFromResponse(response.body());
    }

    /**
     * Resolves API credentials from environment variables or VM properties.
     */
    private String resolveApiKey() {
        String key = System.getenv("GROQ_API_KEY");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        key = System.getProperty("groq.api.key");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        throw new ServiceUnavailableException(
                "No Groq API key configured.\n\n"
              + "• Set environment variable GROQ_API_KEY (recommended), OR\n"
              + "• Add VM option: -Dgroq.api.key=<your-key>\n\n"
              + "In IntelliJ: Run → Edit Configurations → modify Environment variables "
              + "or VM options, then restart the app.", null);
    }

    /**
     * Builds the JSON payload sent to Groq.
     */
    private String buildRequestBody(String userPrompt) {
        String escapedSystem = escapeJson(PromptTemplates.ROADMAP_SYSTEM_PROMPT);
        String escapedUser   = escapeJson(userPrompt);

        return """
                {
                  "model": "%s",
                  "temperature": %s,
                  "response_format": { "type": "json_object" },
                  "messages": [
                    { "role": "system", "content": "%s" },
                    { "role": "user",   "content": "%s" }
                  ]
                }
                """.formatted(MODEL, TEMPERATURE, escapedSystem, escapedUser);
    }

    private HttpResponse<String> sendRequest(String apiKey, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Groq API request was interrupted.", e);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Groq API request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts generated tasks from the Groq response body.
     */
    private List<Task> parseTasksFromResponse(String responseBody) {
        try {
            JsonObject root    = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray  choices = root.getAsJsonArray("choices");
            String content = choices.get(0)
                                    .getAsJsonObject()
                                    .getAsJsonObject("message")
                                    .get("content")
                                    .getAsString();

            content = stripMarkdownCodeFence(content);

            JsonElement parsed = JsonParser.parseString(content);
            JsonArray tasksArray;
            if (parsed.isJsonArray()) {
                tasksArray = parsed.getAsJsonArray();
            } else if (parsed.isJsonObject()) {
                JsonObject contentObj = parsed.getAsJsonObject();
                if (!contentObj.has("tasks")) {
                    throw new ServiceUnavailableException(
                            "Groq JSON missing \"tasks\" key. Raw content starts with: "
                                    + abbreviate(content, 120), null);
                }
                tasksArray = contentObj.getAsJsonArray("tasks");
            } else {
                throw new ServiceUnavailableException(
                        "Unexpected Groq JSON root (expected object or array).", null);
            }

            List<Task> tasks = new ArrayList<>();
            for (JsonElement element : tasksArray) {
                tasks.add(mapToTask(element.getAsJsonObject()));
            }

            if (tasks.isEmpty()) {
                throw new ServiceUnavailableException(
                        "Groq returned an empty task list — response may be malformed.", null);
            }

            return tasks;

        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Failed to parse Groq API response: " + e.getMessage(), e);
        }
    }

    private Task mapToTask(JsonObject obj) {
        Task task = new Task();
        task.setTitle(obj.get("title").getAsString());
        task.setDescription(obj.get("description").getAsString());
        task.setDurationDays(obj.get("duration_days").getAsInt());
        return task;
    }

    /**
     * Strips Markdown code fences when the model returns fenced JSON.
     */
    private static String stripMarkdownCodeFence(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            } else {
                s = s.replaceFirst("^```\\w*", "").trim();
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.trim();
        }
        return s;
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "(null)";
        String t = s.replace('\n', ' ');
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "…";
    }

    /**
     * Escapes prompt text for embedding inside a JSON string literal.
     */
    private static String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
