package com.xiaorui.codellamaai.context;

import org.jetbrains.annotations.NotNull;

/**
 * @author xiaorui
 */
public record EditorContextSnapshot(
        @NotNull String projectName,
        @NotNull String fileName,
        @NotNull String filePath,
        @NotNull String fileType,
        int caretLine,
        @NotNull String selectedText,
        @NotNull String fileExcerpt
) {

    public static EditorContextSnapshot empty(@NotNull String projectName) {
        return new EditorContextSnapshot(projectName, "", "", "", -1, "", "");
    }

    public boolean hasContext() {
        return !fileName.isBlank() || !selectedText.isBlank() || !fileExcerpt.isBlank();
    }
}
