package com.guardian.hadoop.knowledge;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class KnowledgeQuickEntryRequest {

    @NotBlank
    @Size(max = 64)
    private String domain;

    @NotBlank
    private String content;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
