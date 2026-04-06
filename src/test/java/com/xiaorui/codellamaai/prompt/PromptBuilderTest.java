package com.xiaorui.codellamaai.prompt;

import com.xiaorui.codellamaai.chat.ChatMessage;
import com.xiaorui.codellamaai.chat.ChatRequest;
import com.xiaorui.codellamaai.context.EditorContextSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    public void shouldIncludeEditorContextAndHistory() {
        ChatRequest request = new ChatRequest(
                "Explain the bug",
                List.of(
                        ChatMessage.user("What does this method do?"),
                        ChatMessage.assistant("It parses the response.")
                )
        );
        EditorContextSnapshot snapshot = new EditorContextSnapshot(
                "demo-project",
                "SampleService.java",
                "/workspace/SampleService.java",
                "JAVA",
                42,
                "return value == null;",
                "class SampleService { boolean broken() { return value == null; } }"
        );

        String prompt = promptBuilder.buildPrompt(request, snapshot);

        assertTrue(prompt.contains("Project: demo-project"));
        assertTrue(prompt.contains("SampleService.java"));
        assertTrue(prompt.contains("return value == null;"));
        assertTrue(prompt.contains("What does this method do?"));
        assertTrue(prompt.contains("Latest user request:\nExplain the bug"));
    }

    @Test
    public void shouldHandleMissingEditorContext() {
        ChatRequest request = new ChatRequest("Generate a unit test", List.of());
        EditorContextSnapshot snapshot = EditorContextSnapshot.empty("demo-project");

        String prompt = promptBuilder.buildPrompt(request, snapshot);

        assertTrue(prompt.contains("Latest user request:\nGenerate a unit test"));
        assertFalse(prompt.contains("Editor context:\n- File:"));
    }
}
