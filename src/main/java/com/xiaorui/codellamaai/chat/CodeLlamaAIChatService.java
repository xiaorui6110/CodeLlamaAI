package com.xiaorui.codellamaai.chat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.xiaorui.codellamaai.context.EditorContextCollector;
import com.xiaorui.codellamaai.context.EditorContextSnapshot;
import com.xiaorui.codellamaai.ollama.OllamaGateway;
import com.xiaorui.codellamaai.ollama.OllamaModelInfo;
import com.xiaorui.codellamaai.prompt.PromptBuilder;
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

    private static final Logger LOG = Logger.getInstance(CodeLlamaAIChatService.class);

    private final Project project;
    private final OllamaGateway ollamaGateway;
    private final CodeLlamaAISettings settings;
    private final PromptBuilder promptBuilder;
    private final EditorContextCollector contextCollector;

    public CodeLlamaAIChatService(Project project) {
        this.project = project;
        this.ollamaGateway = ApplicationManager.getApplication().getService(OllamaGateway.class);
        this.settings = CodeLlamaAISettings.getInstance();
        this.promptBuilder = new PromptBuilder();
        this.contextCollector = new EditorContextCollector(project);
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

    public CompletableFuture<ChatResult> sendPromptAsync(@NotNull ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String modelName = settings.getModelName();
                if (modelName.isBlank()) {
                    return ChatResult.failure("No Ollama model selected.");
                }

                EditorContextSnapshot contextSnapshot = ReadAction.compute(() -> contextCollector.collect(settings));
                String prompt = promptBuilder.buildPrompt(request, contextSnapshot);
                LOG.info("Prepared chat request. model=" + modelName
                        + ", project=" + project.getName()
                        + ", historySize=" + request.conversationHistory().size()
                        + ", hasContext=" + contextSnapshot.hasContext()
                        + ", selectionLength=" + contextSnapshot.selectedText().length()
                        + ", fileExcerptLength=" + contextSnapshot.fileExcerpt().length()
                        + ", finalPromptLength=" + prompt.length());
                String response = ollamaGateway.chat(
                        settings.getBaseUrl(),
                        modelName,
                        settings.getSystemPrompt(),
                        prompt
                );
                if (response == null || response.isBlank()) {
                    LOG.warn("Ollama returned an empty response. model=" + modelName
                            + ", baseUrl=" + settings.getBaseUrl());
                    return ChatResult.failure("Ollama returned an empty response. Check the model output and IDE log.");
                }
                return ChatResult.success(response);
            } catch (Exception e) {
                LOG.warn("Chat request failed: " + e.getMessage(), e);
                String message = toUserFriendlyMessage(e);
                return ChatResult.failure(message);
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    public Project getProject() {
        return project;
    }

    private String toUserFriendlyMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (message.contains("requires more system memory")) {
            return "The selected Ollama model requires more system memory than is currently available. "
                    + "Choose a smaller model or a more aggressively quantized variant.";
        }
        return message;
    }
}
