CREATE TABLE IF NOT EXISTS knowledge_document_chunk (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(64) NOT NULL,
    document_key VARCHAR(128) NOT NULL,
    document_title VARCHAR(256) NOT NULL,
    section_title VARCHAR(256) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_key VARCHAR(160) NOT NULL,
    source_name VARCHAR(128) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_knowledge_document_chunk_key UNIQUE (chunk_key)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_chunk_document_key
    ON knowledge_document_chunk (document_key);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_chunk_domain
    ON knowledge_document_chunk (domain);
