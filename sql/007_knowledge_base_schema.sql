CREATE TABLE IF NOT EXISTS knowledge_article (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(64) NOT NULL,
    scenario_key VARCHAR(96) NOT NULL UNIQUE,
    title VARCHAR(256) NOT NULL,
    summary TEXT NOT NULL,
    applicability TEXT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    requires_approval BOOLEAN NOT NULL,
    source_name VARCHAR(128) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_symptom_item (
    article_id BIGINT NOT NULL REFERENCES knowledge_article(id),
    order_no INTEGER NOT NULL,
    symptom_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_keyword_item (
    article_id BIGINT NOT NULL REFERENCES knowledge_article(id),
    order_no INTEGER NOT NULL,
    keyword_text VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_step_item (
    article_id BIGINT NOT NULL REFERENCES knowledge_article(id),
    order_no INTEGER NOT NULL,
    step_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_validation_item (
    article_id BIGINT NOT NULL REFERENCES knowledge_article(id),
    order_no INTEGER NOT NULL,
    validation_text TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_caution_item (
    article_id BIGINT NOT NULL REFERENCES knowledge_article(id),
    order_no INTEGER NOT NULL,
    caution_text TEXT NOT NULL
);
