package com.example.demo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.components.JBScrollPane;
import okhttp3.*;
import com.example.demo.VirtualFileUserDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Objects;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class MyToolWindowFactory implements ToolWindowFactory {

    private static final String OPENAI_API_KEY = ""; // TODO: Replace
    private final StringBuilder attachedContext = new StringBuilder();
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());

        // Top: Model selector
        ComboBox<String> modelSelector = new ComboBox<>(new String[]{"gpt-4o", "gpt-3.5-turbo-0125"});
        panel.add(modelSelector, BorderLayout.NORTH);

        // Center: Chat area
        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JBScrollPane chatScrollPane = new JBScrollPane(chatArea);
        panel.add(chatScrollPane, BorderLayout.CENTER);

        // Bottom: Input field + Send button
        JPanel inputWrapper = new JBPanel<>(new BorderLayout());
        inputWrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JTextArea inputField = new JTextArea(3,20);
        inputField.setLayout(new BorderLayout());
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        inputField.requestFocusInWindow();
        inputField.setCaretPosition(inputField.getText().length());

        JPanel buttonPanel = new JPanel(new BorderLayout());

        JButton attachButton = new JButton("+");
        attachButton.setBorder(BorderFactory.createEmptyBorder());
        attachButton.setFocusPainted(false);
        attachButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton sendButton = new JButton("Send");

        sendButton.setBorder(BorderFactory.createEmptyBorder());
        sendButton.setFocusPainted(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // 👈 Sets proper pointer


        buttonPanel.add(attachButton, BorderLayout.WEST);
        buttonPanel.add(sendButton, BorderLayout.EAST);

        inputField.add(buttonPanel, BorderLayout.SOUTH);

        JBScrollPane enterMessageScroll = new JBScrollPane(inputField);




        inputWrapper.add(enterMessageScroll, BorderLayout.CENTER);


        panel.add(inputWrapper, BorderLayout.SOUTH);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);



        attachButton.addActionListener(e -> {
                VirtualFileUserDialog dialog = new VirtualFileUserDialog(project);
               String attachedText = dialog.chooseFileContent();
                if (attachedText != null){
                    attachedContext.append("\n\n[Attached File Content]\n").append(attachedText);
                }});


        // Action when clicking send button
        sendButton.addActionListener(e -> {
            sendMessage(project, modelSelector, chatArea, inputField);
        });

        // Action when pressing Enter in input field
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage(project, modelSelector, chatArea, inputField);
                }
            }
        });
    }

    private void sendMessage(Project project, JComboBox<String> modelSelector, JTextArea chatArea, JTextArea inputField) {
        String userInput = inputField.getText().trim();
        String textToSend = "";


        // If input is empty, fall back to selected text
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                textToSend = userInput + selectedText;
            }else{
                textToSend = userInput + editor.getDocument().getText();
            }
        }
        String finalPrompt = textToSend +attachedContext.toString();



        if (finalPrompt.isEmpty()) {
            chatArea.append("No input or selection provided.\n\n");
            return;
        }

        chatArea.append("You:\n" + textToSend + "\n\n");
        inputField.setText(""); // Clear input field
        sendToOpenAI(finalPrompt, Objects.requireNonNull(modelSelector.getSelectedItem()).toString(), chatArea);
    }

    private void sendToOpenAI(String prompt, String model, JTextArea chatArea) {
        ChatLanguageModel openModel = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName(model)
                .build();
        UserMessage userMessage = UserMessage.from(prompt);
        AiMessage response = AiMessage.from(openModel.generate(prompt));
        System.out.println("response : " + response);
        SwingUtilities.invokeLater(() ->
                chatArea.append("AI:\n" + response.text() + "\n\n")
        );
        /*OkHttpClient client = new OkHttpClient();

        String json = "{\n" +
                "  \"model\": \"" + model + "\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"" + prompt.replace("\"", "\\\"") + "\"}\n" +
                "  ]\n" +
                "}";

        RequestBody body = RequestBody.create(
                json, MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + OPENAI_API_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                SwingUtilities.invokeLater(() ->
                        chatArea.append("Error: " + e.getMessage() + "\n")
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    SwingUtilities.invokeLater(() ->
                            chatArea.append("Request failed: " + response.code() + "\n")
                    );
                } else {
                    String responseBody = response.body().string();
                    String reply = extractContentFromOpenAIResponse(responseBody);
                    SwingUtilities.invokeLater(() ->
                            chatArea.append("AI:\n" + reply + "\n\n")
                    );
                }
            }
        });*/
    }

    private String extractContentFromOpenAIResponse(String responseBody) {
        System.out.print("response from AI : " + responseBody);
        int index = responseBody.indexOf("\"content\":\"");
        if (index != -1) {
            int start = index + 11;
            int end = responseBody.indexOf("\"", start);
            if (end != -1) {
                return responseBody.substring(start, end).replace("\\n", "\n");
            }
        }
        return "No reply.";
    }
}