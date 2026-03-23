package com.xiaorui.codellamaai.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author xiaorui
 */
@Service(Service.Level.APP)
@State(name = "CodeLlamaAISettings", storages = @Storage("CodeLlamaAI.xml"))
public final class CodeLlamaAISettings implements PersistentStateComponent<CodeLlamaAISettings.State> {

    private State state = new State();

    public static CodeLlamaAISettings getInstance() {
        return ApplicationManager.getApplication().getService(CodeLlamaAISettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public String getBaseUrl() {
        return state.baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        state.baseUrl = normalize(baseUrl, "http://localhost:11434");
    }

    public String getModelName() {
        return state.modelName;
    }

    public void setModelName(String modelName) {
        state.modelName = normalize(modelName, "");
    }

    public String getSystemPrompt() {
        return state.systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        state.systemPrompt = normalize(systemPrompt, DEFAULT_SYSTEM_PROMPT);
    }

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are CodeLlamaAI, a concise coding assistant running inside IntelliJ IDEA.";

    private static String normalize(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    public static final class State {
        public String baseUrl = "http://localhost:11434";
        public String modelName = "";
        public String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    }
}
