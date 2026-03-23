package com.xiaorui.codellamaai.chat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.xiaorui.codellamaai.ollama.OllamaGateway;
import com.xiaorui.codellamaai.ollama.OllamaModelInfo;
import com.xiaorui.codellamaai.settings.CodeLlamaAISettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author xiaorui
 */
@Service(Service.Level.PROJECT)
public final class CodeLlamaAIChatService {

    private final Project project;
    private final OllamaGateway ollamaGateway;
    private final CodeLlamaAISettings settings;

    public CodeLlamaAIChatService(Project project) {
        this.project = project;
        this.ollamaGateway = ApplicationManager.getApplication().getService(OllamaGateway.class);
        this.settings = CodeLlamaAISettings.getInstance();
    }

    public CompletableFuture<List<OllamaModelInfo>> refreshModelsAsync(@NotNull String baseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ollamaGateway.listModels(baseUrl);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Model refresh interrupted", e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    public CompletableFuture<ChatResult> sendPromptAsync(@NotNull String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String modelName = settings.getModelName();
                if (modelName.isBlank()) {
                    return ChatResult.failure("No Ollama model selected.");
                }

                String response = ollamaGateway.chat(
                        settings.getBaseUrl(),
                        modelName,
                        settings.getSystemPrompt(),
                        prompt
                );
                return ChatResult.success(response);
            } catch (RuntimeException e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                return ChatResult.failure(message);
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    public Project getProject() {
        return project;
    }
}
