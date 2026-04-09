package com.guardian.hadoop.integration.datasource;

import javax.validation.constraints.NotBlank;

public class LlmPromptRequest {

    @NotBlank
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
