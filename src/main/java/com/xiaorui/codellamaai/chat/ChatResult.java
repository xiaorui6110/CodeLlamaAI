package com.xiaorui.codellamaai.chat;

/**
 * @author xiaorui
 */
public record ChatResult(boolean success, String content) {

    public static ChatResult success(String content) {
        return new ChatResult(true, content);
    }

    public static ChatResult failure(String content) {
        return new ChatResult(false, content);
    }
}
