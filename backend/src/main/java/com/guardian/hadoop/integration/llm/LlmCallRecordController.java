package com.guardian.hadoop.integration.llm;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
public class LlmCallRecordController {

    private final LlmCallRecordService callRecordService;

    public LlmCallRecordController(LlmCallRecordService callRecordService) {
        this.callRecordService = callRecordService;
    }

    @GetMapping("/calls")
    public List<LlmCallRecord> listRecentCalls(@RequestParam(defaultValue = "20") int limit) {
        return callRecordService.listRecent(limit);
    }
}
