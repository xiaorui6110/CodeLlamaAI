package com.xiaorui.codellamaai.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.xiaorui.codellamaai.chat.ChatResult;
import com.xiaorui.codellamaai.chat.CodeLlamaAIChatService;
import com.xiaorui.codellamaai.ollama.OllamaModelInfo;
import com.xiaorui.codellamaai.settings.CodeLlamaAISettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.List;

/**
 * @author xiaorui
 */
public final class CodeLlamaAiToolWindowPanel {

    private final CodeLlamaAISettings settings = CodeLlamaAISettings.getInstance();
    private final CodeLlamaAIChatService chatService;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JTextField baseUrlField = new JTextField();
    private final JComboBox<String> modelComboBox = new JComboBox<>();
    private final JBTextArea systemPromptArea = new JBTextArea(4, 0);
    private final JBTextArea promptArea = new JBTextArea(8, 0);
    private final JBTextArea responseArea = new JBTextArea(14, 0);
    private final JBLabel statusLabel = new JBLabel("Ready");
    private final JButton refreshButton = new JButton("Refresh Models");
    private final JButton sendButton = new JButton("Send");

    public CodeLlamaAiToolWindowPanel(@NotNull Project project) {
        this.chatService = project.getService(CodeLlamaAIChatService.class);
        initFields();
        bindActions();
        content.setBorder(JBUI.Borders.empty(8));
        content.add(buildForm(), BorderLayout.CENTER);
        refreshModels();
    }

    public JComponent getContent() {
        return content;
    }

    private void initFields() {
        baseUrlField.setText(settings.getBaseUrl());
        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);
        systemPromptArea.setText(settings.getSystemPrompt());
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);

        ComboBoxModel<String> modelModel = new DefaultComboBoxModel<>();
        modelComboBox.setModel(modelModel);
        if (!settings.getModelName().isBlank()) {
            modelComboBox.addItem(settings.getModelName());
            modelComboBox.setSelectedItem(settings.getModelName());
        }
    }

    private JComponent buildForm() {
        JPanel actions = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        actions.add(refreshButton, BorderLayout.WEST);
        actions.add(sendButton, BorderLayout.EAST);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Ollama URL", baseUrlField)
                .addLabeledComponent("Model", modelComboBox)
                .addLabeledComponentFillVertically("System Prompt", new JBScrollPane(systemPromptArea))
                .addLabeledComponentFillVertically("Prompt", new JBScrollPane(promptArea))
                .addComponent(actions)
                .addLabeledComponentFillVertically("Response", new JBScrollPane(responseArea))
                .addComponent(statusLabel)
                .getPanel();
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> refreshModels());
        sendButton.addActionListener(event -> sendPrompt());
        modelComboBox.addActionListener(event -> saveSettings());
    }

    private void refreshModels() {
        saveSettings();
        setBusy(true, "Loading models...");
        chatService.refreshModelsAsync(settings.getBaseUrl())
                .whenComplete((models, throwable) -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (throwable != null) {
                        setBusy(false, "Model refresh failed");
                        responseArea.setText(messageFromThrowable(throwable));
                        return;
                    }
                    reloadModels(models);
                    setBusy(false, "Loaded " + models.size() + " model(s)");
                }));
    }

    private void sendPrompt() {
        saveSettings();
        String prompt = promptArea.getText().trim();
        if (prompt.isEmpty()) {
            statusLabel.setText("Prompt is empty");
            return;
        }

        setBusy(true, "Waiting for Ollama...");
        responseArea.setText("");
        chatService.sendPromptAsync(prompt)
                .whenComplete((result, throwable) -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (throwable != null) {
                        setBusy(false, "Chat failed");
                        responseArea.setText(messageFromThrowable(throwable));
                        return;
                    }
                    renderResult(result);
                    setBusy(false, result.success() ? "Completed" : "Failed");
                }));
    }

    private void reloadModels(List<OllamaModelInfo> models) {
        modelComboBox.removeAllItems();
        for (OllamaModelInfo model : models) {
            modelComboBox.addItem(model.name());
        }

        String savedModel = settings.getModelName();
        if (!savedModel.isBlank()) {
            modelComboBox.setSelectedItem(savedModel);
        } else if (!models.isEmpty()) {
            modelComboBox.setSelectedIndex(0);
        }
        saveSettings();
    }

    private void renderResult(ChatResult result) {
        responseArea.setText(result.content());
    }

    private void saveSettings() {
        settings.setBaseUrl(baseUrlField.getText());
        settings.setSystemPrompt(systemPromptArea.getText());
        Object selectedItem = modelComboBox.getSelectedItem();
        settings.setModelName(selectedItem == null ? "" : selectedItem.toString());
    }

    private void setBusy(boolean busy, String status) {
        refreshButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        baseUrlField.setEnabled(!busy);
        modelComboBox.setEnabled(!busy);
        systemPromptArea.setEditable(!busy);
        promptArea.setEditable(!busy);
        statusLabel.setText(status);
    }

    private String messageFromThrowable(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
