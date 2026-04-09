package com.guardian.hadoop.knowledge;

import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final KnowledgeArticleService knowledgeArticleService;
    private final AiGuidanceService aiGuidanceService;

    public KnowledgeController(KnowledgeArticleService knowledgeArticleService, AiGuidanceService aiGuidanceService) {
        this.knowledgeArticleService = knowledgeArticleService;
        this.aiGuidanceService = aiGuidanceService;
    }

    @GetMapping("/knowledge/articles")
    public List<KnowledgeArticleRecord> getKnowledgeArticles() {
        return knowledgeArticleService.listArticles();
    }

    @PostMapping("/knowledge/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeArticleRecord upsertKnowledgeArticle(@Valid @RequestBody KnowledgeArticleRequest request) {
        return knowledgeArticleService.upsert(request);
    }

    @GetMapping("/incidents/{incidentId}/ai-guidance")
    public AiGuidanceRecord getAiGuidance(@PathVariable long incidentId) {
        AiGuidanceRecord record = aiGuidanceService.build(incidentId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return record;
    }
}
