package com.xiaorui.codellamaai.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * @author xiaorui
 */
@Service(Service.Level.APP)
public final class OllamaGateway {

    private static final Logger LOG = Logger.getInstance(OllamaGateway.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public @NotNull List<OllamaModelInfo> listModels(@NotNull String baseUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(baseUrl) + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null) {
            throw new IOException("Unexpected Ollama response status: " + response.statusCode());
        }

        ModelsResponse modelsResponse = objectMapper.readValue(response.body(), ModelsResponse.class);
        return modelsResponse.models().stream()
                .map(model -> new OllamaModelInfo(model.name(), model.size()))
                .sorted(Comparator.comparing(OllamaModelInfo::name))
                .toList();
    }

    public @NotNull String chat(
            @NotNull String baseUrl,
            @NotNull String modelName,
            @NotNull String systemPrompt,
            @NotNull String userPrompt
    ) {
        LOG.info("Sending Ollama chat request. baseUrl=" + trimTrailingSlash(baseUrl)
                + ", model=" + modelName
                + ", systemPromptLength=" + systemPrompt.length()
                + ", userPromptLength=" + userPrompt.length());

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .modelName(modelName)
                .temperature(0.2)
                .timeout(Duration.ofMinutes(2))
                .build();

        String prompt = systemPrompt.isBlank()
                ? userPrompt
                : "System:\n" + systemPrompt + "\n\nUser:\n" + userPrompt;
        String response = model.chat(prompt);
        LOG.info("Received Ollama chat response. model=" + modelName
                + ", responseLength=" + (response == null ? -1 : response.length()));
        return response;
    }

    private String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelsResponse(@JsonProperty("models") List<ModelEntry> models) {

        private ModelsResponse {
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
