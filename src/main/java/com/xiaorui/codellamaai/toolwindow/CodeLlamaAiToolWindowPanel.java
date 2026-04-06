package com.xiaorui.codellamaai.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.xiaorui.codellamaai.chat.ChatMessage;
import com.xiaorui.codellamaai.chat.ChatRequest;
import com.xiaorui.codellamaai.chat.ChatResult;
import com.xiaorui.codellamaai.chat.CodeLlamaAIChatService;
import com.xiaorui.codellamaai.chat.StreamingChatCallbacks;
import com.xiaorui.codellamaai.chat.StreamingChatSession;
import com.xiaorui.codellamaai.ollama.OllamaModelInfo;
import com.xiaorui.codellamaai.settings.CodeLlamaAISettings;
import com.xiaorui.codellamaai.settings.CodeLlamaAISettingsConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author xiaorui
 */
public final class CodeLlamaAiToolWindowPanel {

    private final CodeLlamaAISettings settings = CodeLlamaAISettings.getInstance();
    private final Project project;
    private final CodeLlamaAIChatService chatService;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JComboBox<ModelOption> modelComboBox = new JComboBox<>();
    private final JPanel conversationPanel = new JPanel();
    private final JBTextArea promptArea = new JBTextArea(8, 0);
    private final JBLabel statusLabel = new JBLabel("Ready");
    private final JButton refreshButton = new JButton("Refresh Models");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton clearButton = new JButton("Clear Chat");
    private final JButton sendButton = new JButton("Send");
    private final JButton stopButton = new JButton("Stop");
    private final List<ChatMessage> conversation = new ArrayList<>();
    private boolean updatingModelComboBox;
    private JEditorPane activeStreamingPane;
    private StreamingChatSession activeStreamingSession;

    public CodeLlamaAiToolWindowPanel(@NotNull Project project) {
        this.project = project;
        this.chatService = project.getService(CodeLlamaAIChatService.class);
        initFields();
        bindActions();
        content.setBorder(JBUI.Borders.empty(8));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildConversationView(), BorderLayout.CENTER);
        content.add(buildComposer(), BorderLayout.SOUTH);
        renderConversation();
        refreshModels();
    }

    public JComponent getContent() {
        return content;
    }

    private void initFields() {
        conversationPanel.setLayout(new BoxLayout(conversationPanel, BoxLayout.Y_AXIS));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);

        ComboBoxModel<ModelOption> modelModel = new DefaultComboBoxModel<>();
        modelComboBox.setModel(modelModel);
        modelComboBox.setRenderer(new ModelOptionRenderer());
        if (!settings.getModelName().isBlank()) {
            modelComboBox.addItem(ModelOption.placeholder(settings.getModelName()));
            modelComboBox.setSelectedIndex(0);
        }
    }

    private JComponent buildHeader() {
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        leftActions.add(new JLabel("Model"));
        leftActions.add(modelComboBox);
        leftActions.add(refreshButton);
        leftActions.add(settingsButton);
        leftActions.add(clearButton);
        leftActions.add(stopButton);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.emptyBottom(8));
        header.add(leftActions, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
        return header;
    }

    private JComponent buildConversationView() {
        JBScrollPane scrollPane = new JBScrollPane(conversationPanel);
        scrollPane.setBorder(JBUI.Borders.compound(
                BorderFactory.createTitledBorder("Conversation"),
                JBUI.Borders.empty(4)
        ));
        return scrollPane;
    }

    private JComponent buildComposer() {
        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.setBorder(JBUI.Borders.emptyTop(8));
        actionPanel.add(sendButton, BorderLayout.EAST);

        JPanel composer = new JPanel(new BorderLayout());
        composer.add(new JBScrollPane(promptArea), BorderLayout.CENTER);
        composer.add(actionPanel, BorderLayout.SOUTH);
        composer.setBorder(JBUI.Borders.compound(
                BorderFactory.createTitledBorder("Prompt"),
                JBUI.Borders.empty(4)
        ));
        return composer;
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> refreshModels());
        settingsButton.addActionListener(event -> openSettings());
        clearButton.addActionListener(event -> clearConversation());
        sendButton.addActionListener(event -> sendPrompt());
        stopButton.addActionListener(event -> stopStreaming());
        modelComboBox.addActionListener(event -> saveSettings());
        stopButton.setEnabled(false);
    }

    private void refreshModels() {
        saveSettings();
        setBusy(true, "Loading models...");
        chatService.refreshModelsAsync(settings.getBaseUrl())
                .whenComplete((models, throwable) -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (throwable != null) {
                        setBusy(false, "Model refresh failed");
                        appendSystemMessage("Model refresh failed: " + messageFromThrowable(throwable));
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
        if (settings.getModelName().isBlank()) {
            statusLabel.setText("No model selected");
            appendSystemMessage("Select an Ollama model before sending a prompt.");
            return;
        }

        List<ChatMessage> historyBeforeRequest = List.copyOf(conversation);
        ChatMessage userMessage = ChatMessage.user(prompt);
        conversation.add(userMessage);
        renderConversation();
        setBusy(true, "Waiting for Ollama...");
        promptArea.setText("");

        conversation.add(ChatMessage.assistant(""));
        renderConversation();
        activeStreamingPane = findLastAssistantPane();
        setStreamingState(true, "Streaming response...");

        ChatRequest request = new ChatRequest(prompt, historyBeforeRequest);
        activeStreamingSession = chatService.sendPromptStreaming(request, new StreamingChatCallbacks() {
            @Override
            public void onPartialResponse(@NotNull String partialText) {
                ApplicationManager.getApplication().invokeLater(() -> updateStreamingAssistantMessage(partialText));
            }

            @Override
            public void onComplete(@NotNull String fullText) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    updateStreamingAssistantMessage(fullText);
                    activeStreamingPane = null;
                    activeStreamingSession = null;
                    setStreamingState(false, "Completed");
                });
            }

            @Override
            public void onError(@NotNull String message) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    removeTrailingEmptyAssistantMessage();
                    activeStreamingPane = null;
                    activeStreamingSession = null;
                    appendSystemMessage(message);
                    setStreamingState(false, "Failed");
                });
            }

            @Override
            public void onCancelled() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    removeTrailingEmptyAssistantMessage();
                    activeStreamingPane = null;
                    activeStreamingSession = null;
                    appendSystemMessage("Request cancelled.");
                    setStreamingState(false, "Cancelled");
                });
            }
        });
    }

    private void reloadModels(List<OllamaModelInfo> models) {
        String selectedModelName = selectedModelName();
        updatingModelComboBox = true;
        try {
            modelComboBox.removeAllItems();
            for (OllamaModelInfo model : models) {
                modelComboBox.addItem(new ModelOption(model.name(), model.size()));
            }

            if (!selectedModelName.isBlank()) {
                selectModelByName(selectedModelName);
            } else if (!models.isEmpty()) {
                OllamaModelInfo smallestModel = models.stream()
                        .min(Comparator.comparingLong(OllamaModelInfo::size))
                        .orElse(models.get(0));
                selectModelByName(smallestModel.name());
                appendSystemMessage("Auto-selected the smallest available model: " + smallestModel.name()
                        + " (" + formatSize(smallestModel.size()) + ").");
            }
        } finally {
            updatingModelComboBox = false;
        }
        saveSettings();
    }

    private void renderResult(ChatResult result) {
        conversation.add(result.success()
                ? ChatMessage.assistant(result.content())
                : ChatMessage.system(result.content()));
        renderConversation();
    }

    private void saveSettings() {
        if (updatingModelComboBox) {
            return;
        }
        Object selectedItem = modelComboBox.getSelectedItem();
        if (selectedItem instanceof ModelOption modelOption) {
            settings.setModelName(modelOption.name());
            return;
        }
        settings.setModelName("");
    }

    private void setBusy(boolean busy, String status) {
        refreshButton.setEnabled(!busy);
        settingsButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        modelComboBox.setEnabled(!busy);
        promptArea.setEditable(!busy);
        statusLabel.setText(status);
    }

    private void setStreamingState(boolean streaming, String status) {
        refreshButton.setEnabled(!streaming);
        settingsButton.setEnabled(!streaming);
        clearButton.setEnabled(!streaming);
        sendButton.setEnabled(!streaming);
        stopButton.setEnabled(streaming);
        modelComboBox.setEnabled(!streaming);
        promptArea.setEditable(!streaming);
        statusLabel.setText(status);
    }

    private void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, CodeLlamaAISettingsConfigurable.class);
        refreshModels();
    }

    private void clearConversation() {
        if (activeStreamingSession != null && activeStreamingSession.isRunning()) {
            activeStreamingSession.cancel();
            activeStreamingSession = null;
        }
        activeStreamingPane = null;
        conversation.clear();
        renderConversation();
        statusLabel.setText("Conversation cleared");
    }

    private void stopStreaming() {
        if (activeStreamingSession == null || !activeStreamingSession.isRunning()) {
            statusLabel.setText("No active request");
            stopButton.setEnabled(false);
            return;
        }
        activeStreamingSession.cancel();
        statusLabel.setText("Cancelling...");
    }

    private void renderConversation() {
        conversationPanel.removeAll();
        if (conversation.isEmpty()) {
            JLabel emptyLabel = new JLabel(
                    "<html><body style='padding:6px'>Conversation is empty. Open a file or select code, then ask a question.</body></html>",
                    SwingConstants.LEFT
            );
            emptyLabel.setBorder(JBUI.Borders.empty(4));
            conversationPanel.add(emptyLabel);
        } else {
            for (ChatMessage message : conversation) {
                conversationPanel.add(buildMessageCard(message));
            }
        }
        conversationPanel.revalidate();
        conversationPanel.repaint();
    }

    private JComponent buildMessageCard(ChatMessage message) {
        JEditorPane contentPane = new JEditorPane();
        contentPane.setContentType("text/html");
        contentPane.setEditable(false);
        contentPane.setOpaque(false);
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        contentPane.setBorder(JBUI.Borders.empty(8));
        contentPane.setText(MarkdownRenderer.toHtml(message.content()));
        contentPane.setCaretPosition(0);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.compound(
                BorderFactory.createTitledBorder(titleFor(message)),
                JBUI.Borders.emptyBottom(8)
        ));
        panel.add(contentPane, BorderLayout.CENTER);
        panel.setAlignmentX(0.0f);
        return panel;
    }

    private String titleFor(ChatMessage message) {
        return switch (message.role()) {
            case USER -> "User";
            case ASSISTANT -> "Assistant";
            case SYSTEM -> "System";
        };
    }

    private void appendAssistantMessage(String text) {
        conversation.add(ChatMessage.assistant(text));
        renderConversation();
    }

    private void appendSystemMessage(String text) {
        conversation.add(ChatMessage.system(text));
        renderConversation();
    }

    private void updateStreamingAssistantMessage(String text) {
        if (activeStreamingPane != null) {
            activeStreamingPane.setText(MarkdownRenderer.toHtml(text));
            activeStreamingPane.setCaretPosition(0);
            if (!conversation.isEmpty()) {
                int lastIndex = conversation.size() - 1;
                ChatMessage lastMessage = conversation.get(lastIndex);
                if (lastMessage.role() == ChatMessage.Role.ASSISTANT) {
                    conversation.set(lastIndex, ChatMessage.assistant(text));
                }
            }
            return;
        }
        if (conversation.isEmpty()) {
            conversation.add(ChatMessage.assistant(text));
        } else {
            int lastIndex = conversation.size() - 1;
            ChatMessage lastMessage = conversation.get(lastIndex);
            if (lastMessage.role() == ChatMessage.Role.ASSISTANT) {
                conversation.set(lastIndex, ChatMessage.assistant(text));
            } else {
                conversation.add(ChatMessage.assistant(text));
            }
        }
        renderConversation();
    }

    private void removeTrailingEmptyAssistantMessage() {
        if (conversation.isEmpty()) {
            return;
        }
        int lastIndex = conversation.size() - 1;
        ChatMessage lastMessage = conversation.get(lastIndex);
        if (lastMessage.role() == ChatMessage.Role.ASSISTANT && lastMessage.content().isBlank()) {
            conversation.remove(lastIndex);
            renderConversation();
        }
    }

    private String messageFromThrowable(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void selectModelByName(String modelName) {
        for (int i = 0; i < modelComboBox.getItemCount(); i++) {
            ModelOption option = modelComboBox.getItemAt(i);
            if (option != null && option.name().equals(modelName)) {
                modelComboBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private String selectedModelName() {
        Object selectedItem = modelComboBox.getSelectedItem();
        if (selectedItem instanceof ModelOption modelOption) {
            return modelOption.name();
        }
        return settings.getModelName();
    }

    private JEditorPane findLastAssistantPane() {
        if (conversationPanel.getComponentCount() == 0) {
            return null;
        }
        Component lastComponent = conversationPanel.getComponent(conversationPanel.getComponentCount() - 1);
        if (!(lastComponent instanceof JPanel panel)) {
            return null;
        }
        Component centerComponent = ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        return centerComponent instanceof JEditorPane editorPane ? editorPane : null;
    }

    private String formatSize(long sizeInBytes) {
        if (sizeInBytes <= 0) {
            return "unknown";
        }
        double gibibytes = sizeInBytes / (1024.0 * 1024.0 * 1024.0);
        if (gibibytes >= 1.0) {
            return String.format("%.1f GiB", gibibytes);
        }
        double mebibytes = sizeInBytes / (1024.0 * 1024.0);
        return String.format("%.0f MiB", mebibytes);
    }

    private record ModelOption(@NotNull String name, long size) {

        private static ModelOption placeholder(String name) {
            return new ModelOption(name, -1);
        }
    }

    private final class ModelOptionRenderer extends JLabel implements ListCellRenderer<ModelOption> {

        private ModelOptionRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ModelOption> list,
                ModelOption value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            if (value == null) {
                setText("");
            } else {
                setText(value.name() + " (" + formatSize(value.size()) + ")");
            }
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }
}
