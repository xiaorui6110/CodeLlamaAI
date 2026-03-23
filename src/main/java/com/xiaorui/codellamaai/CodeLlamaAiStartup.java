package com.xiaorui.codellamaai;


import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.xiaorui.codellamaai.chat.CodeLlamaAIChatService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * @description: CodeLlamaAiStartup
 * @author: xiaorui
 * @date: 2026-03-23 13:40
 **/

public class CodeLlamaAiStartup implements ProjectActivity {


    @Override
    public @Nullable Object execute(@NonNull Project project, @NonNull Continuation<? super Unit> continuation) {
        project.getService(CodeLlamaAIChatService.class);
        return null;
    }

}
