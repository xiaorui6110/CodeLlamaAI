package com.xiaorui.codellamaai.chat;

import org.jetbrains.annotations.NotNull;

/**
 * @author xiaorui
 */
public record ChatMessage(@NotNull Role role, @NotNull String content) {


    public enum Role {
        // chat message role
        USER,
        ASSISTANT,
        SYSTEM
    }

    public static ChatMessage user(@NotNull String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(@NotNull String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public static ChatMessage system(@NotNull String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }
}
