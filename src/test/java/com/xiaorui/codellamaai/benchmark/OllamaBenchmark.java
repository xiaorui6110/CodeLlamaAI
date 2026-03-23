package com.xiaorui.codellamaai.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @description: Functional tests for talking to a local Ollama model.
 * @author: xiaorui
 * @date: 2026-03-23 15:36
 */
public class OllamaBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(OllamaBenchmark.class);
    private static final String OLLAMA_URL = System.getProperty(
            "ollama.baseUrl",
            System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434")
    );
    private static final String CONFIGURED_MODEL_NAME = System.getProperty(
            "ollama.model",
            System.getenv("OLLAMA_MODEL")
    );
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void shouldChatWithLocalOllamaModel() {
        Assume.assumeTrue("Ollama service is not reachable: " + OLLAMA_URL, isOllamaReachable());
        String modelName = resolveModelName();
        Assume.assumeTrue("No usable Ollama model found at " + OLLAMA_URL, modelName != null);

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(modelName)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(2))
                .build();

        String prompt = "You are in a connectivity test. Reply with only PONG.";
        String answer;
        try {
            answer = model.chat(prompt);
        } catch (InternalServerException e) {
            if (isInsufficientMemory(e)) {
                Assume.assumeTrue("Skipping because Ollama cannot load model " + modelName + ": " + e.getMessage(), false);
            }
            throw e;
        }

        logger.info("Ollama response from model {}: {}", modelName, answer);
        assertFalse("Ollama response should not be blank", answer == null || answer.isBlank());
        assertTrue("Expected response to contain PONG, but got: " + answer,
                answer.toUpperCase().contains("PONG"));
    }

    private boolean isOllamaReachable() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Ollama availability check status: {}", response.statusCode());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to connect to Ollama at {}", OLLAMA_URL, e);
            return false;
        }
    }

    private String resolveModelName() {
        List<InstalledModel> installedModels = listInstalledModels();
        if (installedModels.isEmpty()) {
            return null;
        }

        if (CONFIGURED_MODEL_NAME != null && !CONFIGURED_MODEL_NAME.isBlank()) {
            for (InstalledModel installedModel : installedModels) {
                if (installedModel.name().equals(CONFIGURED_MODEL_NAME)) {
                    return installedModel.name();
                }
            }
            logger.warn("Configured model {} was not found locally, falling back to the smallest installed model",
                    CONFIGURED_MODEL_NAME);
        }

        return installedModels.stream()
                .min(Comparator.comparingLong(InstalledModel::size))
                .map(InstalledModel::name)
                .orElse(null);
    }

    private List<InstalledModel> listInstalledModels() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(OLLAMA_URL + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return List.of();
            }

            TagsResponse tagsResponse = OBJECT_MAPPER.readValue(response.body(), TagsResponse.class);
            return tagsResponse.models().stream()
                    .map(model -> new InstalledModel(model.name(), model.size()))
                    .toList();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to query model list from Ollama", e);
            return List.of();
        }
    }

    private boolean isInsufficientMemory(InternalServerException e) {
        return e.getMessage() != null && e.getMessage().contains("requires more system memory");
    }

    private record InstalledModel(String name, long size) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TagsResponse(@JsonProperty("models") List<ModelEntry> models) {

        private TagsResponse {
            models = models == null ? List.of() : models;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelEntry(
            @JsonProperty("name") String name,
            @JsonProperty("size") long size
    ) {
    }
}
