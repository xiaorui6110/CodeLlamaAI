package com.xiaorui.codellamaai.context;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.xiaorui.codellamaai.settings.CodeLlamaAISettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author xiaorui
 */
public final class EditorContextCollector {

    private static final int SELECTION_RATIO_DIVISOR = 3;

    private final Project project;

    public EditorContextCollector(@NotNull Project project) {
        this.project = project;
    }

    public @NotNull EditorContextSnapshot collect(@NotNull CodeLlamaAISettings settings) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return EditorContextSnapshot.empty(project.getName());
        }

        Document document = editor.getDocument();
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        String fileName = virtualFile == null ? "" : virtualFile.getName();
        String filePath = virtualFile == null ? "" : virtualFile.getPath();
        String fileType = virtualFile == null || virtualFile.getFileType() == null
                ? ""
                : virtualFile.getFileType().getName();
        int caretOffset = editor.getCaretModel().getOffset();
        int caretLine = document.getLineNumber(Math.max(0, caretOffset)) + 1;

        String selectedText = "";
        if (settings.isIncludeSelection() && editor.getSelectionModel().hasSelection()) {
            selectedText = limit(editor.getSelectionModel().getSelectedText(), Math.max(1000, settings.getContextCharLimit() / SELECTION_RATIO_DIVISOR));
        }

        String fileExcerpt = "";
        if (settings.isIncludeCurrentFile()) {
            fileExcerpt = excerptAroundCaret(document, caretOffset, settings.getContextCharLimit());
        }

        return new EditorContextSnapshot(
                project.getName(),
                fileName,
                filePath,
                fileType,
                caretLine,
                selectedText,
                fileExcerpt
        );
    }

    private String excerptAroundCaret(Document document, int caretOffset, int maxChars) {
        String text = document.getText();
        if (text.isBlank()) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }

        int halfWindow = maxChars / 2;
        int start = Math.max(0, caretOffset - halfWindow);
        int end = Math.min(text.length(), start + maxChars);
        start = Math.max(0, end - maxChars);

        String excerpt = text.substring(start, end);
        if (start > 0) {
            excerpt = "...\n" + excerpt;
        }
        if (end < text.length()) {
            excerpt = excerpt + "\n...";
        }
        return excerpt;
    }

    private String limit(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 4)) + "\n...";
    }
}
