package com.xiaorui.codellamaai.prompt;

import com.xiaorui.codellamaai.chat.ChatMessage;
import com.xiaorui.codellamaai.chat.ChatRequest;
import com.xiaorui.codellamaai.context.EditorContextSnapshot;
import org.jetbrains.annotations.NotNull;

/**
 * @author xiaorui
 */
public final class PromptBuilder {

    private static final int MAX_HISTORY_MESSAGES = 8;

    public @NotNull String buildPrompt(@NotNull ChatRequest request, @NotNull EditorContextSnapshot contextSnapshot) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are assisting with a development task inside IntelliJ IDEA.\n");
        prompt.append("Use the provided editor context when it is relevant, and say when context is missing.\n\n");

        if (contextSnapshot.hasContext()) {
            prompt.append("Editor context:\n");
            prompt.append("- Project: ").append(contextSnapshot.projectName()).append('\n');
            if (!contextSnapshot.fileName().isBlank()) {
                prompt.append("- File: ").append(contextSnapshot.fileName()).append('\n');
            }
            if (!contextSnapshot.filePath().isBlank()) {
                prompt.append("- Path: ").append(contextSnapshot.filePath()).append('\n');
            }
            if (!contextSnapshot.fileType().isBlank()) {
                prompt.append("- File type: ").append(contextSnapshot.fileType()).append('\n');
            }
            if (contextSnapshot.caretLine() > 0) {
                prompt.append("- Caret line: ").append(contextSnapshot.caretLine()).append('\n');
            }
            if (!contextSnapshot.selectedText().isBlank()) {
                prompt.append("\nSelected code:\n```text\n")
                        .append(contextSnapshot.selectedText())
                        .append("\n```\n");
            }
            if (!contextSnapshot.fileExcerpt().isBlank()) {
                prompt.append("\nActive file excerpt:\n```text\n")
                        .append(contextSnapshot.fileExcerpt())
                        .append("\n```\n");
            }
            prompt.append('\n');
        }

        if (!request.conversationHistory().isEmpty()) {
            prompt.append("Recent conversation:\n");
            int startIndex = Math.max(0, request.conversationHistory().size() - MAX_HISTORY_MESSAGES);
            for (int i = startIndex; i < request.conversationHistory().size(); i++) {
                ChatMessage message = request.conversationHistory().get(i);
                prompt.append(message.role().name()).append(":\n");
                prompt.append(message.content()).append("\n\n");
            }
        }

        prompt.append("Latest user request:\n");
        prompt.append(request.userPrompt()).append('\n');
        return prompt.toString();
    }
}
