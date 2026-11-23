-- SupportPilot 数据库结构（MySQL 5.7+ 版本，原 PostgreSQL + pgvector 移植）
-- ddl-auto=none，表结构完全由本脚本管理；所有 CREATE 均 IF NOT EXISTS，保证重启幂等。
-- 向量列在 mock 模式下不使用，统一用 LONGBLOB 占位，避免依赖 pgvector。

SET NAMES utf8mb4;

-- 租户
CREATE TABLE IF NOT EXISTS tenant (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(64),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 产品
CREATE TABLE IF NOT EXISTS product (
    id          BIGINT PRIMARY KEY,
    tenant_id   BIGINT,
    name        VARCHAR(128) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客户（V2.0 EP1）：code 租户内唯一；sla_tier 服务等级枚举；status active/disabled
CREATE TABLE IF NOT EXISTS customer (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT,
    name            VARCHAR(128) NOT NULL,
    code            VARCHAR(64) NOT NULL,
    sla_tier        VARCHAR(16) NOT NULL DEFAULT '默认',
    contract_no     VARCHAR(64),
    contract_start  DATE NULL,
    contract_end    DATE NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_tenant_code (tenant_id, code),
    KEY idx_customer_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客户联系人（V2.0 EP1）：is_primary 标记主联系人，每客户仅一个；同客户内邮箱唯一
CREATE TABLE IF NOT EXISTS contact (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT,
    customer_id BIGINT NOT NULL,
    name        VARCHAR(64) NOT NULL,
    email       VARCHAR(128),
    phone       VARCHAR(32),
    title       VARCHAR(64),
    is_primary  TINYINT(1) NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_contact_customer_email (customer_id, email),
    KEY idx_contact_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工作组（V2.0 EP2 前置：支撑「本组」数据权限；队列/认领/值班留 V2.1）
CREATE TABLE IF NOT EXISTS team (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT,
    name        VARCHAR(64) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_team_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户（user 为保留字，使用 app_user）
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGINT PRIMARY KEY,
    tenant_id   BIGINT,
    username    VARCHAR(64) NOT NULL,
    password    VARCHAR(100) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    email       VARCHAR(128) NULL,
    role        VARCHAR(32),
    team_id     BIGINT NULL,
    data_scope  VARCHAR(16) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_app_user_username ON app_user (username);

-- 工单
CREATE TABLE IF NOT EXISTS ticket (
    id             BIGINT PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    product_id     BIGINT,
    customer_id    BIGINT NULL,
    contact_id     BIGINT NULL,
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
    source         VARCHAR(32),
    source_ref     VARCHAR(512),
    assignee_id    BIGINT,
    env            VARCHAR(64),
    github_updated_at VARCHAR(64),
    first_response_at DATETIME NULL,
    resolved_at    DATETIME NULL,
    sla_paused_seconds BIGINT NOT NULL DEFAULT 0,
    sla_paused_at  DATETIME NULL,
    csat_score     INT NULL,
    csat_comment   VARCHAR(512) NULL,
    csat_at        DATETIME NULL,
    parent_id      BIGINT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ticket_tenant ON ticket (tenant_id);
CREATE INDEX idx_ticket_created ON ticket (created_at);
CREATE INDEX idx_ticket_resolved ON ticket (resolved_at);
CREATE INDEX idx_ticket_status ON ticket (status);
CREATE INDEX idx_ticket_assignee ON ticket (assignee_id);
CREATE INDEX idx_ticket_customer ON ticket (customer_id);
-- 多数据源接入幂等去重：同一来源的同一外部引用只入库一次（NULL 不参与唯一约束，不影响种子数据）
CREATE UNIQUE INDEX uk_ticket_source_ref ON ticket (source, source_ref);

-- 工单会话消息
CREATE TABLE IF NOT EXISTS ticket_message (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT NOT NULL,
    who         VARCHAR(64),
    role        VARCHAR(64),
    text        TEXT,
    internal    TINYINT(1) NOT NULL DEFAULT 0,
    external_id BIGINT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ticket_message_ticket ON ticket_message (ticket_id);
-- 评论幂等去重：同一工单的同一外部评论（GitHub comment id）只入库一次（NULL 不参与唯一约束）
CREATE UNIQUE INDEX uk_ticket_msg_external ON ticket_message (ticket_id, external_id);

-- 知识库文档
CREATE TABLE IF NOT EXISTS kb_doc (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT,
    name         VARCHAR(256),
    path         VARCHAR(512),
    product_line VARCHAR(64),
    chunks       INT NOT NULL DEFAULT 0,
    hits         INT NOT NULL DEFAULT 0,
    status       VARCHAR(32),
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识分片（向量化，真实 RAG 用；mock 模式下 embedding 不使用）
CREATE TABLE IF NOT EXISTS kb_chunk (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doc_id      BIGINT,
    content     TEXT,
    embedding   LONGBLOB,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent 运行记录
CREATE TABLE IF NOT EXISTS agent_run (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT,
    status      VARCHAR(32),
    cost        VARCHAR(64),
    tokens      INT,
    prompt_tokens INT,
    completion_tokens INT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent 步骤
CREATE TABLE IF NOT EXISTS agent_step (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id      BIGINT,
    tag         VARCHAR(32),
    cls         VARCHAR(32),
    `time`      VARCHAR(16),
    html        TEXT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 回复草稿
CREATE TABLE IF NOT EXISTS draft (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT,
    run_id      BIGINT,
    content     TEXT,
    cost        VARCHAR(64),
    status      VARCHAR(32),
    cited_doc_ids   VARCHAR(512),
    feedback_reason VARCHAR(64),
    feedback_comment VARCHAR(1024),
    edited_content  TEXT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评测样本 / 知识盲区（看板 misses）
CREATE TABLE IF NOT EXISTS eval_case (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question    VARCHAR(512),
    count       INT NOT NULL DEFAULT 0,
    weight      INT NOT NULL DEFAULT 0,
    tenant_id   BIGINT,
    source      VARCHAR(32),
    last_asked_at DATETIME NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Spring AI 向量存储表（真实 RAG 启用后使用；mock 模式下不使用，embedding 占位）
CREATE TABLE IF NOT EXISTS vector_store (
    uuid        CHAR(36) PRIMARY KEY,
    content     TEXT,
    metadata    JSON,
    embedding   LONGBLOB
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工单关联 / 合并（#4）：一条记录表示 ticket_id 与 related_ticket_id 的关联，双向可见
CREATE TABLE IF NOT EXISTS ticket_relation (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id         BIGINT NOT NULL,
    related_ticket_id BIGINT NOT NULL,
    relation_type     VARCHAR(32) NOT NULL DEFAULT 'related',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ticket_rel_ticket (ticket_id),
    KEY idx_ticket_rel_related (related_ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 快捷回复模板（#5）：按租户隔离，支持 {客户名} 等变量在回复时替换
CREATE TABLE IF NOT EXISTS reply_template (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT,
    name        VARCHAR(128) NOT NULL,
    content     TEXT,
    category    VARCHAR(64),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reply_tpl_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 幂等迁移：兼容已存在的旧库（MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，用动态 SQL 判断）
-- 新库建表时已含上述列，此处判断存在则跳过；旧库缺列则自动补齐。
-- spring.sql.init.continue-on-error=true，即使某条失败也不阻塞启动。
-- ============================================================
SET @sp_db = DATABASE();

SET @sp_has_source = (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'source');
SET @sp_sql = IF(@sp_has_source = 0, 'ALTER TABLE ticket ADD COLUMN source VARCHAR(32) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_ref = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'source_ref');
SET @sp_sql = IF(@sp_has_ref = 0, 'ALTER TABLE ticket ADD COLUMN source_ref VARCHAR(512) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND INDEX_NAME = 'uk_ticket_source_ref');
SET @sp_sql = IF(@sp_has_idx = 0, 'CREATE UNIQUE INDEX uk_ticket_source_ref ON ticket (source, source_ref)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 补齐处理人字段（assignee_id，关联 app_user.id）及索引（接单 / 转派）
SET @sp_has_assi = (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'assignee_id');
SET @sp_sql = IF(@sp_has_assi = 0, 'ALTER TABLE ticket ADD COLUMN assignee_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_aidx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND INDEX_NAME = 'idx_ticket_assignee');
SET @sp_sql = IF(@sp_has_aidx = 0, 'CREATE INDEX idx_ticket_assignee ON ticket (assignee_id)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 补齐环境描述字段 env（详情 meta 展示）
SET @sp_has_env = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'env');
SET @sp_sql = IF(@sp_has_env = 0, 'ALTER TABLE ticket ADD COLUMN env VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 补齐 github_updated_at（记录上次同步的 issue 更新时间，用于增量同步判断）
SET @sp_has_ghupd = (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'github_updated_at');
SET @sp_sql = IF(@sp_has_ghupd = 0, 'ALTER TABLE ticket ADD COLUMN github_updated_at VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 补齐首次响应 / 解决时间（FRT / MTTR / SLA 达成率指标依赖）
SET @sp_has_fra = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'first_response_at');
SET @sp_sql = IF(@sp_has_fra = 0, 'ALTER TABLE ticket ADD COLUMN first_response_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_ra = (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'resolved_at');
SET @sp_sql = IF(@sp_has_ra = 0, 'ALTER TABLE ticket ADD COLUMN resolved_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 补齐 SLA 暂停计时段（待客户期间不计入 SLA 消耗）
SET @sp_has_sps = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'sla_paused_seconds');
SET @sp_sql = IF(@sp_has_sps = 0, 'ALTER TABLE ticket ADD COLUMN sla_paused_seconds BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_spa = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND COLUMN_NAME = 'sla_paused_at');
SET @sp_sql = IF(@sp_has_spa = 0, 'ALTER TABLE ticket ADD COLUMN sla_paused_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 时间范围 / 导出查询索引
SET @sp_has_cidx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND INDEX_NAME = 'idx_ticket_created');
SET @sp_sql = IF(@sp_has_cidx = 0, 'CREATE INDEX idx_ticket_created ON ticket (created_at)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_ridx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket' AND INDEX_NAME = 'idx_ticket_resolved');
SET @sp_sql = IF(@sp_has_ridx = 0, 'CREATE INDEX idx_ticket_resolved ON ticket (resolved_at)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- agent_run 补齐 token 用量（AI 成本统计依赖）
SET @sp_has_ar_tok = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='agent_run' AND COLUMN_NAME='tokens');
SET @sp_sql = IF(@sp_has_ar_tok = 0, 'ALTER TABLE agent_run ADD COLUMN tokens INT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_ar_ptok = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='agent_run' AND COLUMN_NAME='prompt_tokens');
SET @sp_sql = IF(@sp_has_ar_ptok = 0, 'ALTER TABLE agent_run ADD COLUMN prompt_tokens INT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_ar_ctok = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='agent_run' AND COLUMN_NAME='completion_tokens');
SET @sp_sql = IF(@sp_has_ar_ctok = 0, 'ALTER TABLE agent_run ADD COLUMN completion_tokens INT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- draft 补齐引用文档 / 反馈字段（AI 反馈闭环 #6 #7）
SET @sp_has_dr_cids = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='cited_doc_ids');
SET @sp_sql = IF(@sp_has_dr_cids = 0, 'ALTER TABLE draft ADD COLUMN cited_doc_ids VARCHAR(512) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_dr_fr = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='feedback_reason');
SET @sp_sql = IF(@sp_has_dr_fr = 0, 'ALTER TABLE draft ADD COLUMN feedback_reason VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_dr_fc = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='feedback_comment');
SET @sp_sql = IF(@sp_has_dr_fc = 0, 'ALTER TABLE draft ADD COLUMN feedback_comment VARCHAR(1024) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_dr_ec = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='edited_content');
SET @sp_sql = IF(@sp_has_dr_ec = 0, 'ALTER TABLE draft ADD COLUMN edited_content TEXT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- eval_case 补齐租户隔离 / 来源 / 最近命中时间（AI 反馈闭环 #6）
SET @sp_has_ec_tid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='eval_case' AND COLUMN_NAME='tenant_id');
SET @sp_sql = IF(@sp_has_ec_tid = 0, 'ALTER TABLE eval_case ADD COLUMN tenant_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_ec_src = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='eval_case' AND COLUMN_NAME='source');
SET @sp_sql = IF(@sp_has_ec_src = 0, 'ALTER TABLE eval_case ADD COLUMN source VARCHAR(32) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_ec_la = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='eval_case' AND COLUMN_NAME='last_asked_at');
SET @sp_sql = IF(@sp_has_ec_la = 0, 'ALTER TABLE eval_case ADD COLUMN last_asked_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket_message 补齐 external_id（存 GitHub comment id，用于评论增量去重）及唯一索引
SET @sp_has_extid = (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket_message' AND COLUMN_NAME = 'external_id');
SET @sp_sql = IF(@sp_has_extid = 0, 'ALTER TABLE ticket_message ADD COLUMN external_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_eidx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'ticket_message' AND INDEX_NAME = 'uk_ticket_msg_external');
SET @sp_sql = IF(@sp_has_eidx = 0, 'CREATE UNIQUE INDEX uk_ticket_msg_external ON ticket_message (ticket_id, external_id)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- app_user 补齐登录字段（username / password）及唯一索引
SET @sp_has_uname = (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'username');
SET @sp_sql = IF(@sp_has_uname = 0, 'ALTER TABLE app_user ADD COLUMN username VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_pwd = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'password');
SET @sp_sql = IF(@sp_has_pwd = 0, 'ALTER TABLE app_user ADD COLUMN password VARCHAR(100) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_uidx = (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'app_user' AND INDEX_NAME = 'uk_app_user_username');
SET @sp_sql = IF(@sp_has_uidx = 0, 'CREATE UNIQUE INDEX uk_app_user_username ON app_user (username)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- app_user 补齐邮件地址（通知邮件收件人；为空回退「登录名@mail-domain」）
SET @sp_has_uemail = (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'email');
SET @sp_sql = IF(@sp_has_uemail = 0, 'ALTER TABLE app_user ADD COLUMN email VARCHAR(128) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- contact 补齐邮件地址（老库若缺列则补上；与 CREATE TABLE 中的 email 同口径）
SET @sp_has_cemail = (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = @sp_db AND TABLE_NAME = 'contact' AND COLUMN_NAME = 'email');
SET @sp_sql = IF(@sp_has_cemail = 0, 'ALTER TABLE contact ADD COLUMN email VARCHAR(128) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket 补齐 CSAT 满意度字段（#3）与父工单 parent_id（#4）
SET @sp_has_csat_s = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='csat_score');
SET @sp_sql = IF(@sp_has_csat_s = 0, 'ALTER TABLE ticket ADD COLUMN csat_score INT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_csat_c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='csat_comment');
SET @sp_sql = IF(@sp_has_csat_c = 0, 'ALTER TABLE ticket ADD COLUMN csat_comment VARCHAR(512) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_csat_a = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='csat_at');
SET @sp_sql = IF(@sp_has_csat_a = 0, 'ALTER TABLE ticket ADD COLUMN csat_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;
SET @sp_has_pid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='parent_id');
SET @sp_sql = IF(@sp_has_pid = 0, 'ALTER TABLE ticket ADD COLUMN parent_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ============================================================
-- 操作审计日志：记录谁在何时对什么做了什么，含操作前/后快照、请求参数、结果、IP 等。
-- 索引内联在 CREATE TABLE 中，配合 IF NOT EXISTS 保证重启幂等。
-- created_at 为操作发生时间（由应用写入），DATETIME 与现有表口径一致。
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_log (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT,
    operator_id       BIGINT,
    operator_name     VARCHAR(64),
    operator_username VARCHAR(64),
    module            VARCHAR(32)  NOT NULL,
    action            VARCHAR(32)  NOT NULL,
    target_type       VARCHAR(32),
    target_id         VARCHAR(64),
    summary           VARCHAR(512),
    before_data       TEXT,
    after_data        TEXT,
    request_params    TEXT,
    result            VARCHAR(16)  NOT NULL,
    error_msg         TEXT,
    ip                VARCHAR(64),
    user_agent        VARCHAR(256),
    cost_ms           INT,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_tenant_created (tenant_id, created_at),
    KEY idx_operator (operator_id),
    KEY idx_module_action (module, action),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V2.0：客户域 / 通知中心 新表（建表均 IF NOT EXISTS，重启幂等）
-- ============================================================

-- 站内信（V2.0 EP3）：按用户未读查询为主，索引 (user_id, is_read, created_at)
CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(32) NOT NULL,
    title       VARCHAR(128) NOT NULL,
    content     VARCHAR(512),
    ref_type    VARCHAR(32),
    ref_id      VARCHAR(64),
    is_read     TINYINT(1) NOT NULL DEFAULT 0,
    read_at     DATETIME NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notif_user_read_created (user_id, is_read, created_at),
    KEY idx_notif_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 通知规则（V2.0 EP3）：event_type × receivers × 通道开关；receivers 存角色/用户标识，逗号分隔
CREATE TABLE IF NOT EXISTS notify_rule (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT,
    event_type      VARCHAR(32) NOT NULL,
    name            VARCHAR(64),
    condition_json  VARCHAR(512),
    receivers       VARCHAR(256) NOT NULL,
    in_app_enabled  TINYINT(1) NOT NULL DEFAULT 1,
    email_enabled   TINYINT(1) NOT NULL DEFAULT 0,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notify_rule_event (tenant_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 邮件任务（V2.0 EP3）：异步发送 + 重试状态机；status: pending/sent/failed
CREATE TABLE IF NOT EXISTS mail_task (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT,
    notification_id BIGINT,
    to_addr         VARCHAR(256) NOT NULL,
    subject         VARCHAR(256) NOT NULL,
    content         TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',
    retry_count     INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(512),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_mail_task_status (status),
    KEY idx_mail_task_notif (notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V2.0 幂等迁移：补齐旧库缺失的新列与索引（新库建表已含，此处仅补旧库）
-- ============================================================

-- ticket 扩列：挂接客户 / 联系人
SET @sp_has_cid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='customer_id');
SET @sp_sql = IF(@sp_has_cid = 0, 'ALTER TABLE ticket ADD COLUMN customer_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_ctid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='contact_id');
SET @sp_sql = IF(@sp_has_ctid = 0, 'ALTER TABLE ticket ADD COLUMN contact_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_tcidx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND INDEX_NAME='idx_ticket_customer');
SET @sp_sql = IF(@sp_has_tcidx = 0, 'CREATE INDEX idx_ticket_customer ON ticket (customer_id)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- app_user 扩列：工作组 + 数据权限范围（SELF/TEAM/ALL）
SET @sp_has_tid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='app_user' AND COLUMN_NAME='team_id');
SET @sp_sql = IF(@sp_has_tid = 0, 'ALTER TABLE app_user ADD COLUMN team_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_ds = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='app_user' AND COLUMN_NAME='data_scope');
SET @sp_sql = IF(@sp_has_ds = 0, 'ALTER TABLE app_user ADD COLUMN data_scope VARCHAR(16) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- 存量角色映射：V1.x admin/engineer/null -> 新枚举 ADMIN/ENGINEER（幂等：再次执行匹配 0 行）
UPDATE app_user SET role='ADMIN' WHERE role='admin';
UPDATE app_user SET role='ENGINEER' WHERE role='engineer';
UPDATE app_user SET role='ENGINEER' WHERE role IS NULL OR role NOT IN ('ADMIN','MANAGER','ENGINEER','KB_ADMIN');

-- V2.0 EP2：回填存量用户 data_scope（按角色默认，幂等：仅更新 NULL 行）
UPDATE app_user SET data_scope = CASE WHEN role IN ('ADMIN','MANAGER') THEN 'ALL' ELSE 'TEAM' END
WHERE data_scope IS NULL;

-- ============================================================
-- V2.1 服务履约升级：SLA 策略 / 工作日历 / 节假日 / 工单附件
-- 建表均 IF NOT EXISTS，扩列均 information_schema 判断，重启幂等
-- ============================================================

-- 计时日历：工作日位图（1=周一 … 7=周日）、最多 3 段工作时段、时区
CREATE TABLE IF NOT EXISTS sla_calendar (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT NOT NULL DEFAULT 0,
    name        VARCHAR(64) NOT NULL,
    workdays    VARCHAR(32) NOT NULL DEFAULT '1,2,3,4,5',
    segments    VARCHAR(128) NOT NULL DEFAULT '09:00-18:00',
    timezone    VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled     TINYINT(1) NOT NULL DEFAULT 1,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sla_calendar_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 节假日：日历维度，不计入工作时长
CREATE TABLE IF NOT EXISTS sla_holiday (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    calendar_id   BIGINT NOT NULL,
    holiday_date  DATE NOT NULL,
    name          VARCHAR(64),
    UNIQUE KEY uk_sla_holiday (calendar_id, holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SLA 策略矩阵：服务等级 × 优先级 唯一
CREATE TABLE IF NOT EXISTS sla_policy (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT NOT NULL DEFAULT 0,
    tier                  VARCHAR(16) NOT NULL,
    priority              VARCHAR(8) NOT NULL,
    first_response_minutes INT NOT NULL,
    resolve_minutes       INT NOT NULL,
    calendar_type         VARCHAR(16) NOT NULL DEFAULT 'NATURAL',
    calendar_id           BIGINT,
    warning_threshold     INT NOT NULL DEFAULT 80,
    enabled               TINYINT(1) NOT NULL DEFAULT 1,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sla_policy (tenant_id, tier, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工单附件：挂在工单主体或某条消息上，逻辑删除 / 清理分离
CREATE TABLE IF NOT EXISTS attachment (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT,
    ticket_id    BIGINT NOT NULL,
    message_id   BIGINT,
    uploader_id  BIGINT,
    file_name    VARCHAR(255) NOT NULL,
    ext          VARCHAR(16),
    mime         VARCHAR(128),
    size_bytes   BIGINT NOT NULL,
    sha256       CHAR(64) NOT NULL,
    storage_key  VARCHAR(512) NOT NULL,
    visibility   VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
    deleted      TINYINT(1) NOT NULL DEFAULT 0,
    purged       TINYINT(1) NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_att_ticket (ticket_id, deleted, purged),
    KEY idx_att_message (message_id),
    KEY idx_att_hash (ticket_id, sha256),
    KEY idx_att_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ticket 补齐 SLA 快照字段（V2.1 FR-S02 / FR-S03 / FR-S11）
SET @sp_has_sla_pid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_policy_id');
SET @sp_sql = IF(@sp_has_sla_pid = 0, 'ALTER TABLE ticket ADD COLUMN sla_policy_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_frd = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='first_response_due_at');
SET @sp_sql = IF(@sp_has_frd = 0, 'ALTER TABLE ticket ADD COLUMN first_response_due_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_rd = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='resolve_due_at');
SET @sp_sql = IF(@sp_has_rd = 0, 'ALTER TABLE ticket ADD COLUMN resolve_due_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_sst = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_state');
SET @sp_sql = IF(@sp_has_sst = 0, 'ALTER TABLE ticket ADD COLUMN sla_state VARCHAR(16) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_sct = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_calendar_type');
SET @sp_sql = IF(@sp_has_sct = 0, 'ALTER TABLE ticket ADD COLUMN sla_calendar_type VARCHAR(16) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_swt = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_warning_threshold');
SET @sp_sql = IF(@sp_has_swt = 0, 'ALTER TABLE ticket ADD COLUMN sla_warning_threshold INT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_scid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_calendar_id');
SET @sp_sql = IF(@sp_has_scid = 0, 'ALTER TABLE ticket ADD COLUMN sla_calendar_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_sws = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_warning_sent_at');
SET @sp_sql = IF(@sp_has_sws = 0, 'ALTER TABLE ticket ADD COLUMN sla_warning_sent_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_sbs = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='sla_breached_at');
SET @sp_sql = IF(@sp_has_sbs = 0, 'ALTER TABLE ticket ADD COLUMN sla_breached_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- 扫描任务按状态过滤
SET @sp_has_sla_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND INDEX_NAME='idx_ticket_sla');
SET @sp_sql = IF(@sp_has_sla_idx = 0, 'CREATE INDEX idx_ticket_sla ON ticket (sla_state, status)', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ============================================================
-- V2.3 智能服务升级：相似归组 / 故障风暴 / AI 评测 / L0 自助 /
-- 情绪预警 / 会话摘要 / 客户健康分
-- 建表均 IF NOT EXISTS，扩列均 information_schema 判断，重启幂等
-- ============================================================

-- M1 相似工单：记录某工单与组内相似工单的相似度
CREATE TABLE IF NOT EXISTS ticket_similarity (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id          BIGINT NOT NULL,
    similar_ticket_id  BIGINT NOT NULL,
    score              DOUBLE NOT NULL DEFAULT 0,
    channel            VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    group_id           BIGINT,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_similar (ticket_id, similar_ticket_id),
    KEY idx_ticket_similar_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M2 故障风暴：同分类短窗口内工单激增的聚合记录
CREATE TABLE IF NOT EXISTS storm_record (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    storm_key        VARCHAR(128) NOT NULL,
    category         VARCHAR(64),
    group_id         VARCHAR(64),
    `count`          INT NOT NULL DEFAULT 0,
    window_start     DATETIME NULL,
    window_minutes   INT NOT NULL DEFAULT 30,
    threshold        INT NOT NULL DEFAULT 5,
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    event_ticket_id  BIGINT,
    last_ticket_id   BIGINT,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_storm_status (status),
    KEY idx_storm_key (storm_key, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M3 AI 评测用例：从草稿采纳 / 驳回沉淀
CREATE TABLE IF NOT EXISTS ai_eval_case (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id        BIGINT,
    question         VARCHAR(512),
    expected_points  TEXT,
    accept_criteria  TEXT,
    reject_reason    VARCHAR(64),
    status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    tenant_id        BIGINT,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_eval_case_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M3 评测运行：记录每次回归的通过率与较上次变化
CREATE TABLE IF NOT EXISTS ai_eval_run (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version_label    VARCHAR(64),
    total            INT NOT NULL DEFAULT 0,
    passed           INT NOT NULL DEFAULT 0,
    pass_rate        DOUBLE NOT NULL DEFAULT 0,
    prev_pass_rate   DOUBLE,
    detail_json      LONGTEXT,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M4 L0 自动解决配置：按分类白名单 + 阈值
CREATE TABLE IF NOT EXISTS auto_resolve_config (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category    VARCHAR(64) NOT NULL,
    enabled     TINYINT(1) NOT NULL DEFAULT 1,
    threshold   DOUBLE NOT NULL DEFAULT 0.90,
    tenant_id   BIGINT,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_auto_resolve (tenant_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M5 情绪分析结果：按消息记录
CREATE TABLE IF NOT EXISTS sentiment_result (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT NOT NULL,
    message_id  BIGINT,
    sentiment   VARCHAR(16) NOT NULL DEFAULT 'NEUTRAL',
    intensity   DOUBLE NOT NULL DEFAULT 0,
    keywords    VARCHAR(256),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sentiment_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M7 客户健康分日快照
CREATE TABLE IF NOT EXISTS health_score_daily (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT NOT NULL,
    score_date       DATE NOT NULL,
    score            DOUBLE NOT NULL DEFAULT 0,
    freq_score       DOUBLE NOT NULL DEFAULT 0,
    sla_score        DOUBLE NOT NULL DEFAULT 0,
    csat_score       DOUBLE NOT NULL DEFAULT 0,
    sentiment_score  DOUBLE NOT NULL DEFAULT 0,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_health_customer_date (customer_id, score_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M6 会话摘要：保留 AI 原始版与人工修正版
CREATE TABLE IF NOT EXISTS ticket_summary (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id         BIGINT NOT NULL,
    original_content  TEXT,
    edited_content    TEXT,
    generated_at      DATETIME NULL,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_summary (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M1 相似检测配置
CREATE TABLE IF NOT EXISTS similarity_config (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,
    threshold    DOUBLE NOT NULL DEFAULT 0.82,
    enabled      TINYINT(1) NOT NULL DEFAULT 1,
    window_days  INT NOT NULL DEFAULT 30,
    UNIQUE KEY uk_similarity_config (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- M7 健康分权重配置
CREATE TABLE IF NOT EXISTS health_score_config (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    freq_weight       INT NOT NULL DEFAULT 30,
    sla_weight        INT NOT NULL DEFAULT 25,
    csat_weight       INT NOT NULL DEFAULT 25,
    sentiment_weight  INT NOT NULL DEFAULT 20,
    UNIQUE KEY uk_health_config (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ticket 扩列：V2.3 相似组 / L0 自助状态
SET @sp_has_sgid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='similarity_group_id');
SET @sp_sql = IF(@sp_has_sgid = 0, 'ALTER TABLE ticket ADD COLUMN similarity_group_id BIGINT NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_l0 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='l0_state');
SET @sp_sql = IF(@sp_has_l0 = 0, 'ALTER TABLE ticket ADD COLUMN l0_state VARCHAR(24) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_l0c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='l0_closed_at');
SET @sp_sql = IF(@sp_has_l0c = 0, 'ALTER TABLE ticket ADD COLUMN l0_closed_at DATETIME NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_l0t = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='l0_confirm_token');
SET @sp_sql = IF(@sp_has_l0t = 0, 'ALTER TABLE ticket ADD COLUMN l0_confirm_token VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_l0r = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='l0_reopen_count');
SET @sp_sql = IF(@sp_has_l0r = 0, 'ALTER TABLE ticket ADD COLUMN l0_reopen_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_l0h = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket' AND COLUMN_NAME='l0_handler_name');
SET @sp_sql = IF(@sp_has_l0h = 0, 'ALTER TABLE ticket ADD COLUMN l0_handler_name VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- ticket_message 扩列：情绪分析结果
SET @sp_has_sent = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket_message' AND COLUMN_NAME='sentiment');
SET @sp_sql = IF(@sp_has_sent = 0, 'ALTER TABLE ticket_message ADD COLUMN sentiment VARCHAR(16) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_senti = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='ticket_message' AND COLUMN_NAME='sentiment_intensity');
SET @sp_sql = IF(@sp_has_senti = 0, 'ALTER TABLE ticket_message ADD COLUMN sentiment_intensity DOUBLE NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- draft 扩列：置信度与版本（模型 / 提示词 / 知识库）
SET @sp_has_conf = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='confidence');
SET @sp_sql = IF(@sp_has_conf = 0, 'ALTER TABLE draft ADD COLUMN confidence DOUBLE NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_mv = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='model_version');
SET @sp_sql = IF(@sp_has_mv = 0, 'ALTER TABLE draft ADD COLUMN model_version VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_pv = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='prompt_version');
SET @sp_sql = IF(@sp_has_pv = 0, 'ALTER TABLE draft ADD COLUMN prompt_version VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

SET @sp_has_kv = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@sp_db AND TABLE_NAME='draft' AND COLUMN_NAME='kb_version');
SET @sp_sql = IF(@sp_has_kv = 0, 'ALTER TABLE draft ADD COLUMN kb_version VARCHAR(64) NULL', 'SELECT 1');
PREPARE sp_stmt FROM @sp_sql; EXECUTE sp_stmt; DEALLOCATE PREPARE sp_stmt;

-- GitHub Issue 同步配置（全局单行）：仓库 / 指定 Issue / 同步条数 / 评论条数（0=全部）
CREATE TABLE IF NOT EXISTS github_sync_config (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    enabled          TINYINT(1) NOT NULL DEFAULT 1,
    owner            VARCHAR(100) NOT NULL,
    repo             VARCHAR(100) NOT NULL,
    issue_number     INT NULL,
    max_issues       INT NOT NULL DEFAULT 20,
    max_comments     INT NOT NULL DEFAULT 20,
    token            VARCHAR(255) NULL,
    last_sync_at     DATETIME NULL,
    last_sync_result VARCHAR(255) NULL,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
