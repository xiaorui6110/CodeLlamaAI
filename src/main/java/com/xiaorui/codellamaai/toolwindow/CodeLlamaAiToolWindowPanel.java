package com.xiaorui.codellamaai.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.ui.JBColor;
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
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.text.DefaultEditorKit;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author xiaorui
 */
public final class CodeLlamaAiToolWindowPanel {

    private static final String STATUS_READY = "Ready";
    private static final String STATUS_LOADING_MODELS = "Loading models...";
    private static final String STATUS_MODEL_REFRESH_FAILED = "Model refresh failed";
    private static final String STATUS_PROMPT_EMPTY = "Prompt is empty";
    private static final String STATUS_NO_MODEL = "No model selected";
    private static final String STATUS_SENDING = "Sending request...";
    private static final String STATUS_STREAMING = "Streaming response...";
    private static final String STATUS_COMPLETED = "Completed";
    private static final String STATUS_FAILED = "Failed";
    private static final String STATUS_CANCELLED = "Cancelled";
    private static final String STATUS_CANCELLING = "Cancelling...";
    private static final String STATUS_CONVERSATION_CLEARED = "Conversation cleared";
    private static final String STATUS_NO_ACTIVE_REQUEST = "No active request";

    private final CodeLlamaAISettings settings = CodeLlamaAISettings.getInstance();
    private final Project project;
    private final CodeLlamaAIChatService chatService;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JComboBox<ModelOption> modelComboBox = new JComboBox<>();
    private final JPanel conversationPanel = new JPanel();
    private final JBScrollPane conversationScrollPane = new JBScrollPane(conversationPanel);
    private final JBTextArea promptArea = new JBTextArea(8, 0);
    private final JBLabel statusLabel = new JBLabel(STATUS_READY);
    private final JBLabel shortcutHintLabel = new JBLabel("Ctrl+Enter to send. Shift+Enter for newline.");
    private final JButton refreshButton = new JButton("Refresh Models");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton clearButton = new JButton("Clear Chat");
    private final JButton sendButton = new JButton("Send");
    private final JButton stopButton = new JButton("Stop");
    private final List<ChatMessage> conversation = new ArrayList<>();
    private boolean updatingModelComboBox;
    private boolean autoScrollEnabled = true;
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
        shortcutHintLabel.setBorder(JBUI.Borders.empty(0, 2, 0, 0));

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
        conversationScrollPane.setBorder(JBUI.Borders.compound(
                BorderFactory.createTitledBorder("Conversation"),
                JBUI.Borders.empty(4)
        ));
        conversationScrollPane.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            autoScrollEnabled = isNearBottom();
        });
        return conversationScrollPane;
    }

    private JComponent buildComposer() {
        JPanel hintPanel = new JPanel(new BorderLayout());
        hintPanel.setOpaque(false);
        hintPanel.add(shortcutHintLabel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        actionPanel.setOpaque(false);
        actionPanel.setBorder(JBUI.Borders.emptyTop(8));
        actionPanel.add(hintPanel, BorderLayout.WEST);
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
        promptArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                "codellama.sendPrompt"
        );
        promptArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                DefaultEditorKit.insertBreakAction
        );
        promptArea.getActionMap().put("codellama.sendPrompt", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (sendButton.isEnabled()) {
                    sendPrompt();
                }
            }
        });
        stopButton.setEnabled(false);
        updateInteractiveState();
    }

    private void refreshModels() {
        saveSettings();
        setBusy(true, STATUS_LOADING_MODELS);
        chatService.refreshModelsAsync(settings.getBaseUrl())
                .whenComplete((models, throwable) -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (throwable != null) {
                        setBusy(false, STATUS_MODEL_REFRESH_FAILED);
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
            setStatus(STATUS_PROMPT_EMPTY);
            return;
        }
        if (settings.getModelName().isBlank()) {
            setStatus(STATUS_NO_MODEL);
            appendSystemMessage("Select an Ollama model before sending a prompt.");
            return;
        }

        List<ChatMessage> historyBeforeRequest = List.copyOf(conversation);
        ChatMessage userMessage = ChatMessage.user(prompt);
        conversation.add(userMessage);
        renderConversation();
        autoScrollEnabled = true;
        setBusy(true, STATUS_SENDING);
        promptArea.setText("");

        conversation.add(ChatMessage.assistant(""));
        renderConversation();
        activeStreamingPane = findLastAssistantPane();
        setStreamingState(true, STATUS_STREAMING);

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
                    setStreamingState(false, STATUS_COMPLETED);
                    scrollConversationToBottom();
                });
            }

            @Override
            public void onError(@NotNull String message) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    removeTrailingEmptyAssistantMessage();
                    activeStreamingPane = null;
                    activeStreamingSession = null;
                    appendSystemMessage(message);
                    setStreamingState(false, STATUS_FAILED);
                    scrollConversationToBottom();
                });
            }

            @Override
            public void onCancelled() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    removeTrailingEmptyAssistantMessage();
                    activeStreamingPane = null;
                    activeStreamingSession = null;
                    appendSystemMessage("Request cancelled.");
                    setStreamingState(false, STATUS_CANCELLED);
                    scrollConversationToBottom();
                });
            }
        });
        updateInteractiveState();
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
        updateInteractiveState();
        setStatus(status);
    }

    private void setStreamingState(boolean streaming, String status) {
        refreshButton.setEnabled(!streaming);
        settingsButton.setEnabled(!streaming);
        clearButton.setEnabled(!streaming);
        sendButton.setEnabled(!streaming);
        stopButton.setEnabled(streaming);
        modelComboBox.setEnabled(!streaming);
        promptArea.setEditable(!streaming);
        updateInteractiveState();
        setStatus(status);
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
        setStatus(STATUS_CONVERSATION_CLEARED);
    }

    private void stopStreaming() {
        if (activeStreamingSession == null || !activeStreamingSession.isRunning()) {
            setStatus(STATUS_NO_ACTIVE_REQUEST);
            stopButton.setEnabled(false);
            return;
        }
        activeStreamingSession.cancel();
        setStatus(STATUS_CANCELLING);
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
                conversationPanel.add(JBUI.Panels.simplePanel().withBorder(JBUI.Borders.emptyBottom(8)));
            }
        }
        conversationPanel.revalidate();
        conversationPanel.repaint();
    }

    private JComponent buildMessageCard(ChatMessage message) {
        MarkdownRenderer.RenderedMarkdown renderedMarkdown = MarkdownRenderer.render(message.content());

        JEditorPane contentPane = new JEditorPane();
        contentPane.setContentType("text/html");
        contentPane.setEditable(false);
        contentPane.setOpaque(false);
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        contentPane.setBorder(JBUI.Borders.empty(8));
        contentPane.setText(renderedMarkdown.html());
        contentPane.setCaretPosition(0);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        JLabel titleLabel = new JLabel(titleFor(message));
        titleLabel.setBorder(JBUI.Borders.empty(0, 6, 6, 6));
        titlePanel.add(titleLabel, BorderLayout.WEST);

        if (message.role() == ChatMessage.Role.ASSISTANT) {
            JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0));
            actionButtons.setOpaque(false);

            JButton copyButton = new JButton("Copy");
            copyButton.addActionListener(event -> copyToClipboard(message.content(), "Copied response"));
            actionButtons.add(copyButton);

            if (MarkdownRenderer.hasCodeBlock(message.content())) {
                JButton copyCodeButton = new JButton("Copy Code");
                copyCodeButton.addActionListener(event -> copyCodeBlocks(message.content()));
                actionButtons.add(copyCodeButton);
            }

            titlePanel.add(actionButtons, BorderLayout.EAST);
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.compound(
                BorderFactory.createLineBorder(borderColorFor(message), 1, true),
                JBUI.Borders.empty(8)
        ));
        panel.setBackground(backgroundColorFor(message));
        titlePanel.setBackground(backgroundColorFor(message));
        contentPane.setBackground(backgroundColorFor(message));
        panel.add(titlePanel, BorderLayout.NORTH);
        panel.add(buildMessageBody(message, contentPane, renderedMarkdown), BorderLayout.CENTER);
        panel.setAlignmentX(0.0f);
        return panel;
    }

    private JComponent buildMessageBody(
            ChatMessage message,
            JEditorPane contentPane,
            MarkdownRenderer.RenderedMarkdown renderedMarkdown
    ) {
        if (message.role() != ChatMessage.Role.ASSISTANT || renderedMarkdown.codeBlocks().isEmpty()) {
            return contentPane;
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(contentPane);

        int codeBlockIndex = 1;
        for (String codeBlock : renderedMarkdown.codeBlocks()) {
            body.add(buildCodeBlockPanel(codeBlock, codeBlockIndex++));
        }
        return body;
    }

    private JComponent buildCodeBlockPanel(String code, int index) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel label = new JLabel("Code Block " + index);
        label.setBorder(JBUI.Borders.empty(6, 0, 4, 0));
        header.add(label, BorderLayout.WEST);

        JButton copyButton = new JButton("Copy Block");
        copyButton.addActionListener(event -> copyToClipboard(code, "Copied code block"));
        header.add(copyButton, BorderLayout.EAST);

        JBTextArea codeArea = new JBTextArea(code);
        codeArea.setEditable(false);
        codeArea.setLineWrap(false);
        codeArea.setWrapStyleWord(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, codeArea.getFont().getSize()));
        codeArea.setBorder(JBUI.Borders.empty(8));
        codeArea.setBackground(new JBColor(new Color(248, 250, 252), new Color(43, 43, 43)));

        JScrollPane scrollPane = new JBScrollPane(codeArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new JBColor(new Color(206, 212, 218), new Color(96, 99, 102))));
        scrollPane.setAlignmentX(0.0f);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setBorder(JBUI.Borders.emptyTop(4));
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
        scrollConversationToBottom();
    }

    private void appendSystemMessage(String text) {
        conversation.add(ChatMessage.system(text));
        renderConversation();
        scrollConversationToBottom();
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
            scrollConversationToBottom();
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
        scrollConversationToBottom();
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

    private void scrollConversationToBottom() {
        if (!autoScrollEnabled) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (conversationScrollPane.getVerticalScrollBar() == null) {
                return;
            }
            conversationScrollPane.getVerticalScrollBar().setValue(
                    conversationScrollPane.getVerticalScrollBar().getMaximum()
            );
        });
    }

    private void copyToClipboard(String text, String statusMessage) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
        setStatus(statusMessage);
    }

    private void copyCodeBlocks(String markdown) {
        String codeBlocks = MarkdownRenderer.extractCodeBlocks(markdown);
        if (codeBlocks.isBlank()) {
            setStatus("No code block found");
            return;
        }
        copyToClipboard(codeBlocks, "Copied code block");
    }

    private boolean isNearBottom() {
        JScrollPane scrollPane = conversationScrollPane;
        int value = scrollPane.getVerticalScrollBar().getValue();
        int extent = scrollPane.getVerticalScrollBar().getModel().getExtent();
        int maximum = scrollPane.getVerticalScrollBar().getMaximum();
        return maximum - (value + extent) < JBUI.scale(32);
    }

    private void updateInteractiveState() {
        sendButton.setText(activeStreamingSession != null && activeStreamingSession.isRunning() ? "Sending..." : "Send");
        stopButton.setText(activeStreamingSession != null && activeStreamingSession.isRunning() ? "Stop" : "Stop");
        shortcutHintLabel.setEnabled(activeStreamingSession == null || !activeStreamingSession.isRunning());
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private Color backgroundColorFor(ChatMessage message) {
        return switch (message.role()) {
            case USER -> new JBColor(new Color(232, 242, 255), new Color(44, 55, 72));
            case ASSISTANT -> new JBColor(new Color(245, 247, 250), new Color(60, 63, 65));
            case SYSTEM -> new JBColor(new Color(255, 244, 229), new Color(79, 58, 40));
        };
    }

    private Color borderColorFor(ChatMessage message) {
        return switch (message.role()) {
            case USER -> new JBColor(new Color(120, 162, 255), new Color(86, 127, 189));
            case ASSISTANT -> new JBColor(new Color(206, 212, 218), new Color(96, 99, 102));
            case SYSTEM -> new JBColor(new Color(255, 179, 71), new Color(163, 112, 50));
        };
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
