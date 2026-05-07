package com.guardian.hadoop.sql;

import java.util.List;

public class SqlOptimizationRuleAnalysis {

    private final String normalizedSql;
    private final String riskLevel;
    private final List<String> findings;
    private final List<String> rewriteHints;

    public SqlOptimizationRuleAnalysis(String normalizedSql,
                                       String riskLevel,
                                       List<String> findings,
                                       List<String> rewriteHints) {
        this.normalizedSql = normalizedSql;
        this.riskLevel = riskLevel;
        this.findings = findings;
        this.rewriteHints = rewriteHints;
    }

    public String getNormalizedSql() {
        return normalizedSql;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public List<String> getFindings() {
        return findings;
    }

    public List<String> getRewriteHints() {
        return rewriteHints;
    }
}
