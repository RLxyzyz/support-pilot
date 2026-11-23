# SupportPilot V2.3 智能服务升级 · 实现规格

## 1. 背景与现状

- 后端：Spring Boot 3.3.5 / Java 21 / MySQL；`ddl-auto=none`，表结构由 `backend/src/main/resources/db/schema-mysql.sql` 管理（幂等 CREATE + information_schema 动态 ALTER + `spring.sql.init.continue-on-error=true`）。
- 前端：React 18 + Vite + TS；页面在 `frontend/src/pages`，路由 `App.tsx`，侧栏 `layouts/Shell.tsx`，接口封装 `api/client.ts`，类型 `types/index.ts`。
- 已完成 V2.1：SLA 策略/日历/扫描（SlaPolicyService、SlaScanService、SlaService）、原子认领（TicketService.claim）、附件（AttachmentService）。
- 本次目标：实现 PRD V2.3 的 M1–M7 共 34 条 FR，优先真实能力；无外部依赖时降级为确定性的本地算法（字符 bigram 相似度、词典情绪、规则置信度），不得阻断主流程。

## 2. 总体设计原则

1. 智能能力均为「旁路增强」：写操作（建单/发消息）主流程成功后再执行，`try/catch` 包裹，失败仅记录日志。
2. 所有新接口走既有约定：`Result<T>` 返回、`@OperationAudit`、`@RequireRole`、`TenantContext`/`UserContext`。
3. 新增配置均落库（按租户唯一），并提供 GET/PUT 接口，PUT 需权限与审计。
4. 新表/新列全部追加到 `schema-mysql.sql` 末尾，保持幂等。
5. 前端只做「可见、可操作」的最小闭环：徽标、抽屉、卡片、配置页。

## 3. 数据模型（追加到 schema-mysql.sql）

### 3.1 新表

| 表 | 关键列 |
|---|---|
| `ticket_similarity` | id, ticket_id, similar_ticket_id, score DOUBLE, channel VARCHAR(16), group_id BIGINT, created_at；唯一 `(ticket_id, similar_ticket_id)`；索引 group_id |
| `storm_record` | id, storm_key VARCHAR(128), category VARCHAR(64), group_id VARCHAR(64), count INT, window_start DATETIME, window_minutes INT, threshold INT, status VARCHAR(16), event_ticket_id BIGINT, last_ticket_id BIGINT, created_at, updated_at |
| `ai_eval_case` | id, ticket_id, question VARCHAR(512), expected_points TEXT, accept_criteria TEXT, reject_reason VARCHAR(64), status VARCHAR(16), tenant_id, created_at |
| `ai_eval_run` | id, version_label VARCHAR(64), total INT, passed INT, pass_rate DOUBLE, prev_pass_rate DOUBLE, detail_json LONGTEXT, created_at |
| `auto_resolve_config` | id, category VARCHAR(64), enabled TINYINT(1), threshold DOUBLE DEFAULT 0.90, tenant_id, updated_at；唯一 `(tenant_id, category)` |
| `sentiment_result` | id, ticket_id, message_id, sentiment VARCHAR(16), intensity DOUBLE, keywords VARCHAR(256), created_at；索引 ticket_id |
| `health_score_daily` | id, customer_id, score_date DATE, score DOUBLE, freq_score DOUBLE, sla_score DOUBLE, csat_score DOUBLE, sentiment_score DOUBLE, created_at；唯一 `(customer_id, score_date)` |
| `ticket_summary` | id, ticket_id 唯一, original_content TEXT, edited_content TEXT, generated_at, updated_at |
| `similarity_config` | id, tenant_id 唯一, threshold DOUBLE DEFAULT 0.82, enabled TINYINT(1) DEFAULT 1, window_days INT DEFAULT 30 |
| `health_score_config` | id, tenant_id 唯一, freq_weight INT DEFAULT 30, sla_weight INT DEFAULT 25, csat_weight INT DEFAULT 25, sentiment_weight INT DEFAULT 20 |

### 3.2 新列

- `ticket`：`similarity_group_id BIGINT`、`l0_state VARCHAR(24)`、`l0_closed_at TIMESTAMP NULL`、`l0_confirm_token VARCHAR(64)`、`l0_reopen_count INT DEFAULT 0`、`l0_handler_name VARCHAR(64)`。
- `ticket_message`：`sentiment VARCHAR(16)`、`sentiment_intensity DOUBLE`。
- `draft`：`confidence DOUBLE`、`model_version VARCHAR(64)`、`prompt_version VARCHAR(64)`、`kb_version VARCHAR(64)`。

迁移方式：复用现有 `information_schema` 动态 SQL 模板逐列 ALTER。

## 4. 后端实现

### 4.1 新增实体 / Repository

实体（`com.supportpilot.entity`）：`TicketSimilarity`、`StormRecord`、`AiEvalCase`、`AiEvalRun`、`AutoResolveConfig`、`SentimentResult`、`HealthScoreDaily`、`TicketSummary`、`SimilarityConfig`、`HealthScoreConfig`。
Repository（`com.supportpilot.repository`）：对应 Spring Data JPA 接口，含所需 finder（按 tenant、按日期区间、按 group、按 ticket 等）。

### 4.2 M1 相似工单归组（FR-M1-01..05）

新增 `SimilarityService`：
- `detectAndGroup(Ticket)`：同租户近 `window_days` 天工单，标题+正文做字符 bigram Jaccard；取 top5 且 `score >= threshold`；命中则确定 groupId（候选已有 group 或自身 id），写自身 `ticket.similarityGroupId` 与 `ticket_similarity`（channel=TEXT，双向仅存单向记录）。
- `GET /api/tickets/{id}/similar` → `{groupId,count,items:[{id,title,status,score,isMain}]}`。
- `POST /api/tickets/{id}/similar/batch-reply` `{content,ticketIds?}`：逐单独立事务发送可见回复 + 审计，返回 `{sent,failed:[{id,message}]}`。
- `POST /api/tickets/{id}/similar/merge` `{targetId}`：复用 `ticket_relation(relation_type=duplicate)` 并设 `parent_id`。
- `GET/PUT /api/similarity/config`（ADMIN）。

挂载点：`TicketService.create` 保存后调用 `detectAndGroup`（try/catch）。

### 4.3 M2 故障风暴识别（FR-M2-01..05）

新增 `StormService`：
- `@Scheduled(cron = "0 * * * * *")`：统计近 30 分钟同分类新建工单数；≥5 落 `storm_record(ACTIVE)`；同 `storm_key` 活跃仅累加并更新 last_ticket_id，不重复告警；首次创建发 `STORM_ALERT` 给 MANAGER。
- `GET /api/storms/active`。
- `POST /api/storms/{id}/upgrade`：创建分类=事件 的工单、批量 `ticket_relation` 关联组内工单、写 `event_ticket_id`。
- `POST /api/storms/{id}/recover`：事件单置「已解决」，状态 RECOVERED。
- `POST /api/storms/{id}/dismiss`：状态 DISMISSED。

### 4.4 M3 AI 草稿采纳与评测（FR-M3-01..06）

新增 `EvalService`：
- `GET /api/eval/adoption`：按分类/工程师聚合 draft 采纳率、驳回率、平均修改幅度。
- 驳回原因枚举校验（信息不完整/结论不准确/语气不合适/知识库缺失/其他，其他必填），写入 `ai_eval_case`；接入 `DraftService.reject`。
- `POST /api/eval/cases/sediment`：从 draft 沉淀用例；`GET /api/eval/cases`。
- `POST /api/eval/runs`：用例 <30 返回 400；以 KB 关键词覆盖率判定通过，输出 `passRate/prevPassRate`，写 `ai_eval_run`。
- `GET /api/eval/clusters`：低分按驳回原因聚类。
- draft 生成时写 `confidence/model_version/prompt_version/kb_version`（默认值兜底）。

### 4.5 M4 L0 自助闭环（FR-M4-01..06）

新增 `L0Service`：
- `GET/PUT /api/l0/config`：白名单分类 + enabled + 阈值；PUT 必须 `confirm=true`，否则 400，写审计。
- 建单后异步尝试：白名单分类且置信度 ≥ 阈值 → `status=AI 处理中`、`l0_state=AUTO_REPLIED`、写带「AI 助手回复」标识与转人工入口的可见消息、生成 72h confirm token；否则照常「待分诊」。
- 语义拦截词（退费/退款/赔偿/投诉/合同/解约/法务/起诉）命中即转人工并记录原因。
- `POST /api/l0/confirm?token=`、`POST /api/l0/reopen/{ticketId}`（回待分诊、reopen_count+1、优先派回相似组主单处理人）、`POST /api/l0/transfer/{ticketId}`、`GET /api/l0/report`（按分类解决率/误关率/转人工率/拦截量）。

### 4.6 M5 情绪预警（FR-M5-01..04）

新增 `SentimentService`：
- 词典法：正/负面词与程度副词计算 `sentiment` + `intensity`，写 `ticket_message.sentiment/sentiment_intensity` 与 `sentiment_result`。
- 负面强度 ≥0.8 或同单累计 2 条负面 → `SENTIMENT_ALERT`（处理人 + MANAGER）。
- `POST /api/tickets/{id}/sentiment/upgrade`：优先级低于 P2 时调 P2 并审计。
- `GET /api/customers/{id}/sentiment/trend`：近 90 天按天负面占比/均强度。
- 消息 VO 带 `sentiment` 字段（前端弱化展示）。

挂载点：`TicketService.addMessage` 保存后调用（try/catch）。

### 4.7 M6 会话摘要（FR-M6-01..03）

新增 `SummaryService`：
- `GET /api/tickets/{id}/summary`：消息 ≥10 条时生成（LLM JSON：问题概述/已做尝试/当前卡点/客户诉求/下一步建议；失败用规则模板），落 `ticket_summary`。
- `PUT /api/tickets/{id}/summary`：保存修正版，保留 `original_content`。
- `TicketVO` 带 `summary`（无则 null）。

### 4.8 M7 客户健康分（FR-M7-01..04）

新增 `HealthService`：
- `@Scheduled(cron = "0 0 2 * * *")`：活跃客户健康分 `100 − 频率(0-30) − SLA违约(0-25) − CSAT(0-25，无数据不扣) − 情绪(0-20)`，写 `health_score_daily`。
- <60 或周环比降 ≥15 → `HEALTH_ALERT`（每客户每周最多 1 次）。
- `GET /api/customers/{id}/health`：当前分数/分项/90 天趋势；`GET/PUT /api/health/config`。

### 4.9 公共改动

- `NotifyType`：新增 `STORM_ALERT`、`SENTIMENT_ALERT`、`HEALTH_ALERT` 及 LABELS。
- `TicketVO`：新增 `summary`、`similarGroupId`、`similarCount`、`l0State`、`l0ReopenCount`、`sentiment`（最近负面标识）。
- `TicketMessageVO`：新增 `sentiment`、`sentimentIntensity`。
- `TicketService`：create/addMessage 挂载旁路能力；新增相似组状态字段填充。
- 控制器：`SimilarityController`、`StormController`、`EvalController`、`L0Controller`、`SentimentController`、`SummaryController`、`HealthController`（情绪/摘要/健康可分别并入 Ticket/Customer 控制器，但保持路径与 PRD 一致）。

## 5. 前端实现

- `types/index.ts`：新增相似/风暴/评测/L0/情绪/摘要/健康相关类型。
- `api/client.ts`：新增对应方法与类型化调用。
- `TicketList.tsx`：相似徽标 + 抽屉（组内列表 + 批量回复）+ 风暴横幅。
- `TicketDetail.tsx`：摘要置顶卡、情绪标识、L0 状态卡、相似卡。
- `CustomerDetail.tsx`：健康分环形 + 分项 + 90 天趋势 + 情绪趋势。
- `Board.tsx`：采纳率趋势卡 + L0 四指标卡。
- 新增 `SmartSettings.tsx`（智能配置：相似阈值、L0 白名单、健康权重、评测用例/回归），`App.tsx` 加路由，`Shell.tsx` 启用「评测集」入口。

## 6. 验收与验证

1. `cd backend && mvn -q -DskipTests package` 编译通过；`mvn -q test` 通过（至少不漏编译）。
2. `cd frontend && npm run build` 通过。
3. 接口冒烟：相似检测、风暴扫描、评测运行、L0 配置/确认、情绪升级、摘要生成、健康分计算均可返回 `Result.ok`。
4. 关键负向用例：评测用例 <30 → 400；L0 PUT 无 confirm → 400；认领冲突 → 409（既有）。

## 7. 非目标

- 不接入真实 LLM / Milvus（保留降级实现与接口形状，后续可替换）。
- 不改动 V2.1 SLA/认领/附件既有行为。
