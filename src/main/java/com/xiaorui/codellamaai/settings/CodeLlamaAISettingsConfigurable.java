package com.xiaorui.codellamaai.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * @author xiaorui
 */
public final class CodeLlamaAISettingsConfigurable implements Configurable {

    private final CodeLlamaAISettings settings = CodeLlamaAISettings.getInstance();

    private JPanel panel;
    private JTextField baseUrlField;
    private JBTextArea systemPromptArea;
    private JBCheckBox includeCurrentFileCheckBox;
    private JBCheckBox includeSelectionCheckBox;
    private JTextField contextCharLimitField;

    @Override
    public @Nls String getDisplayName() {
        return "CodeLlamaAI";
    }

    @Override
    public @Nullable JComponent createComponent() {
        baseUrlField = new JTextField();
        systemPromptArea = new JBTextArea(6, 0);
        includeCurrentFileCheckBox = new JBCheckBox("Include active file context");
        includeSelectionCheckBox = new JBCheckBox("Prefer selected code when available");
        contextCharLimitField = new JTextField();

        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);

        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Ollama URL", baseUrlField)
                .addLabeledComponentFillVertically("System prompt", new JBScrollPane(systemPromptArea))
                .addComponent(includeCurrentFileCheckBox)
                .addComponent(includeSelectionCheckBox)
                .addLabeledComponent("Context character limit", contextCharLimitField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        if (panel == null) {
            return false;
        }
        return !settings.getBaseUrl().equals(baseUrlField.getText().trim())
                || !settings.getSystemPrompt().equals(systemPromptArea.getText().trim())
                || settings.isIncludeCurrentFile() != includeCurrentFileCheckBox.isSelected()
                || settings.isIncludeSelection() != includeSelectionCheckBox.isSelected()
                || settings.getContextCharLimit() != parseContextCharLimit();
    }

    @Override
    public void apply() {
        settings.setBaseUrl(baseUrlField.getText());
        settings.setSystemPrompt(systemPromptArea.getText());
        settings.setIncludeCurrentFile(includeCurrentFileCheckBox.isSelected());
        settings.setIncludeSelection(includeSelectionCheckBox.isSelected());
        settings.setContextCharLimit(parseContextCharLimit());
    }

    @Override
    public void reset() {
        if (panel == null) {
            return;
        }
        baseUrlField.setText(settings.getBaseUrl());
        systemPromptArea.setText(settings.getSystemPrompt());
        includeCurrentFileCheckBox.setSelected(settings.isIncludeCurrentFile());
        includeSelectionCheckBox.setSelected(settings.isIncludeSelection());
        contextCharLimitField.setText(String.valueOf(settings.getContextCharLimit()));
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        baseUrlField = null;
        systemPromptArea = null;
        includeCurrentFileCheckBox = null;
        includeSelectionCheckBox = null;
        contextCharLimitField = null;
    }

    private int parseContextCharLimit() {
        try {
            return Integer.parseInt(contextCharLimitField.getText().trim());
        } catch (NumberFormatException ignored) {
            return settings.getContextCharLimit();
        }
    }
}
