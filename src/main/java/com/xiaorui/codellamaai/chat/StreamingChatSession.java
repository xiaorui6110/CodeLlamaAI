package com.xiaorui.codellamaai.chat;

/**
 * @author xiaorui
 */
public interface StreamingChatSession {

    void cancel();

    boolean isRunning();
}
