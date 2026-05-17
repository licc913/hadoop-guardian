package com.guardian.hadoop.integration.llm;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LlmCallRecordService {

    private final JdbcTemplate jdbcTemplate;

    public LlmCallRecordService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute(
            "create table if not exists llm_call_record ("
                + "id bigserial primary key,"
                + "feature varchar(64) not null,"
                + "model varchar(128),"
                + "status varchar(32) not null,"
                + "prompt_chars integer not null,"
                + "response_chars integer not null,"
                + "duration_ms bigint,"
                + "error_message text,"
                + "prompt_preview text,"
                + "created_at timestamp not null,"
                + "completed_at timestamp"
                + ")"
        );
        jdbcTemplate.execute("create index if not exists idx_llm_call_record_created_at on llm_call_record(created_at desc)");
        jdbcTemplate.execute("create index if not exists idx_llm_call_record_feature on llm_call_record(feature, created_at desc)");
    }

    public Long start(String feature, String model, String prompt) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject(
            "insert into llm_call_record "
                + "(feature, model, status, prompt_chars, response_chars, duration_ms, error_message, prompt_preview, created_at, completed_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            Long.class,
            defaultIfBlank(feature, "UNKNOWN"),
            trimToNull(model),
            "RUNNING",
            length(prompt),
            0,
            null,
            null,
            preview(prompt),
            toTimestamp(now),
            null
        );
    }

    public void finish(Long id, String responseText) {
        if (id == null) {
            return;
        }
        Instant completedAt = Instant.now();
        Instant createdAt = queryCreatedAt(id);
        jdbcTemplate.update(
            "update llm_call_record set status = ?, response_chars = ?, duration_ms = ?, completed_at = ? where id = ?",
            "COMPLETED",
            length(responseText),
            createdAt == null ? null : Duration.between(createdAt, completedAt).toMillis(),
            toTimestamp(completedAt),
            id
        );
    }

    public void fail(Long id, String errorMessage) {
        if (id == null) {
            return;
        }
        Instant completedAt = Instant.now();
        Instant createdAt = queryCreatedAt(id);
        jdbcTemplate.update(
            "update llm_call_record set status = ?, duration_ms = ?, error_message = ?, completed_at = ? where id = ?",
            "FAILED",
            createdAt == null ? null : Duration.between(createdAt, completedAt).toMillis(),
            truncate(defaultIfBlank(errorMessage, "unknown error"), 2000),
            toTimestamp(completedAt),
            id
        );
    }

    public List<LlmCallRecord> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return jdbcTemplate.query(
            "select id, feature, model, status, prompt_chars, response_chars, duration_ms, "
                + "error_message, prompt_preview, created_at, completed_at "
                + "from llm_call_record order by created_at desc limit ?",
            (rs, rowNum) -> new LlmCallRecord(
                rs.getLong("id"),
                rs.getString("feature"),
                rs.getString("model"),
                rs.getString("status"),
                rs.getInt("prompt_chars"),
                rs.getInt("response_chars"),
                nullableLong(rs, "duration_ms"),
                rs.getString("error_message"),
                rs.getString("prompt_preview"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("completed_at"))
            ),
            safeLimit
        );
    }

    private Instant queryCreatedAt(Long id) {
        try {
            Timestamp value = jdbcTemplate.queryForObject("select created_at from llm_call_record where id = ?", Timestamp.class, id);
            return toInstant(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long nullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String preview(String value) {
        return truncate(defaultIfBlank(value, ""), 4000);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
