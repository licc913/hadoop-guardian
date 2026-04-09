package com.guardian.hadoop.integration.datasource;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticScriptValidationService {

    private final DiagnosticScriptRepository repository;

    public DiagnosticScriptValidationService(DiagnosticScriptRepository repository) {
        this.repository = repository;
    }

    public IntegrationTestResponse validateScripts() {
        List<DiagnosticScriptEntity> enabledScripts = repository.findAllByOrderByServiceScopeAscScriptNameAsc().stream()
            .filter(DiagnosticScriptEntity::isEnabled)
            .collect(Collectors.toList());

        if (enabledScripts.isEmpty()) {
            return new IntegrationTestResponse(
                false,
                false,
                "当前没有可校验的启用脚本。",
                "请至少新增一条启用状态的诊断脚本配置。",
                "",
                Instant.now()
            );
        }

        StringBuilder details = new StringBuilder();
        boolean allValid = true;
        for (DiagnosticScriptEntity script : enabledScripts) {
            String status;
            if (!hasText(script.getScriptName()) || !hasText(script.getCommandPath())) {
                status = "配置不完整";
                allValid = false;
            } else if (!pathExists(script.getCommandPath())) {
                status = "路径不存在";
                allValid = false;
            } else {
                status = "可访问";
            }

            if (details.length() > 0) {
                details.append("\n");
            }
            details.append(script.getScriptName()).append(" | ").append(script.getCommandPath()).append(" | ").append(status);
        }

        return new IntegrationTestResponse(
            allValid,
            true,
            allValid ? "诊断脚本校验通过。" : "诊断脚本校验未通过。",
            details.toString(),
            "已检查 " + enabledScripts.size() + " 条启用脚本",
            Instant.now()
        );
    }

    private boolean pathExists(String commandPath) {
        try {
            return Files.exists(Paths.get(commandPath.trim()));
        } catch (InvalidPathException ex) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
