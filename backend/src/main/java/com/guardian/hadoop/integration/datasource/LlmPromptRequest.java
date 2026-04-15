package com.guardian.hadoop.integration.datasource;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class LlmPromptRequest {

    @NotBlank
    private String question;
    private List<LlmChatMessage> history = new ArrayList<LlmChatMessage>();

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<LlmChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<LlmChatMessage> history) {
        this.history = history == null ? new ArrayList<LlmChatMessage>() : history;
    }
}
