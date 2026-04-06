package com.xiaorui.codellamaai.chat;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author xiaorui
 */
public record ChatRequest(@NotNull String userPrompt, @NotNull List<ChatMessage> conversationHistory) {

    public ChatRequest {
        conversationHistory = List.copyOf(conversationHistory);
    }
}
