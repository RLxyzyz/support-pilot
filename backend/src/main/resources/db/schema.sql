-- SupportPilot 数据库结构（PostgreSQL + pgvector）
-- ddl-auto=none，表结构完全由本脚本管理；生产环境由迁移工具接管。
-- 所有 CREATE 均 IF NOT EXISTS，保证重启幂等。

CREATE EXTENSION IF NOT EXISTS vector;

-- 租户
CREATE TABLE IF NOT EXISTS tenant (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(64),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 产品
CREATE TABLE IF NOT EXISTS product (
    id          BIGINT PRIMARY KEY,
    tenant_id   BIGINT,
    name        VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户（user 为保留字，使用 app_user）
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGINT PRIMARY KEY,
    tenant_id   BIGINT,
    name        VARCHAR(64) NOT NULL,
    role        VARCHAR(32),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 工单
CREATE TABLE IF NOT EXISTS ticket (
    id             BIGINT PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    product_id     BIGINT,
    title          VARCHAR(512) NOT NULL,
    body           TEXT,
    code           TEXT,
    priority       VARCHAR(8),
    priority_class VARCHAR(16),
    status         VARCHAR(32),
    status_class   VARCHAR(16),
    category       VARCHAR(64),
    sla            VARCHAR(64),
    sla_pct        INT,
    sla_color      VARCHAR(16),
    update_time    VARCHAR(32),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ticket_tenant ON ticket (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ticket_status ON ticket (status);

-- 工单会话消息
CREATE TABLE IF NOT EXISTS ticket_message (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT NOT NULL,
    who         VARCHAR(64),
    role        VARCHAR(64),
    text        TEXT,
    internal    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ticket_message_ticket ON ticket_message (ticket_id);

-- 知识库文档
CREATE TABLE IF NOT EXISTS kb_doc (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT,
    name         VARCHAR(256),
    path         VARCHAR(512),
    product_line VARCHAR(64),
    chunks       INT NOT NULL DEFAULT 0,
    hits         INT NOT NULL DEFAULT 0,
    status       VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 知识分片（向量化，真实 RAG 用）
CREATE TABLE IF NOT EXISTS kb_chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      BIGINT,
    content     TEXT,
    embedding   vector(1024),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Agent 运行记录
CREATE TABLE IF NOT EXISTS agent_run (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT,
    status      VARCHAR(32),
    cost        VARCHAR(64),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Agent 步骤
CREATE TABLE IF NOT EXISTS agent_step (
    id          BIGSERIAL PRIMARY KEY,
    run_id      BIGINT,
    tag         VARCHAR(32),
    cls         VARCHAR(32),
    time        VARCHAR(16),
    html        TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 回复草稿
CREATE TABLE IF NOT EXISTS draft (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT,
    run_id      BIGINT,
    content     TEXT,
    cost        VARCHAR(64),
    status      VARCHAR(32),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 评测样本 / 知识盲区（看板 misses）
CREATE TABLE IF NOT EXISTS eval_case (
    id          BIGSERIAL PRIMARY KEY,
    question    VARCHAR(512),
    count       INT NOT NULL DEFAULT 0,
    weight      INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Spring AI pgvector 向量存储表（真实 RAG 启用后使用）
CREATE TABLE IF NOT EXISTS vector_store (
    uuid        UUID PRIMARY KEY,
    content     TEXT,
    metadata    JSON,
    embedding   vector(1024)
);
