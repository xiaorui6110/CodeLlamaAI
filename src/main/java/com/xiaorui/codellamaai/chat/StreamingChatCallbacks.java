package com.xiaorui.codellamaai.chat;

import org.jetbrains.annotations.NotNull;

/**
 * @author xiaorui
 */
public interface StreamingChatCallbacks {

    void onPartialResponse(@NotNull String partialText);

    void onComplete(@NotNull String fullText);

    void onError(@NotNull String message);

    void onCancelled();
}
