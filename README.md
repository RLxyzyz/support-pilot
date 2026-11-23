# SupportPilot 智能工单系统

面向技术支持团队的 AI 工单处理系统。工单管理、GitHub Issue 双向同步与自动归户、AI Copilot 流式生成回复草稿、客户/联系人管理、SLA 策略与自动升级、邮件+站内信双通道通知、知识库 RAG 检索。前后端分离，一键 Docker 部署。

![架构](https://img.shields.io/badge/arch-Frontend%20%2B%20Backend%20%2B%20MySQL%20%2B%20Milvus-blue) ![Java](https://img.shields.io/badge/Java-21-orange) ![React](https://img.shields.io/badge/React-18-61dafb) ![MySQL](https://img.shields.io/badge/MySQL-8.4-00758f)

---

## 架构总览

```
                        ┌──────────────────────┐
                        │  浏览器 / Nginx :80    │
                        │  前端静态资源          │
                        │  /api 反代 → backend   │
                        └──────────┬───────────┘
                                   │
                        ┌──────────▼───────────┐
                        │  Spring Boot :8080    │
                        │  JWT · 多租户 · RBAC   │
                        │  SSE · Scheduler       │
                        └──┬───────┬───────┬───┘
                           │       │       │
              JPA / MySQL 8    Milvus     SMTP 邮件
              （本地容器）     （远程）     （QQ/企业邮）
```

**数据库（MySQL 8.4 容器内）**：20+ 业务表，由 `schema-mysql.sql` 启动时幂等建表；演示种子数据可选。

**向量库（远程 Milvus）**：知识库文档分块后向量化，按语义相似度检索。向量化默认使用阿里云百炼 `text-embedding-v4`，余弦相似度检索。

**大模型**：对话用 DeepSeek（`deepseek-flash`），支持 SSE 流式输出；接入任何 OpenAI 兼容网关只需改 `AI_BASE_URL` 和 `AI_MODEL`。

**鉴权**：JWT 签名，30 分钟无任何请求自动退出；有操作会滑动续期（响应头 `X-Refreshed-Token`）。公网部署默认关闭自助注册。

**通知**：所有事件（分派、转派、SLA 预警/超时、回复客户）支持站内信 + 邮件双通道，后台定时任务 20 秒轮询发送，失败按 1/5/15 分钟退避重试。

**GitHub 同步**：可开关，定时全量拉取最近 N 条 issue，过滤 PR，按作者自动创建客户/联系人并归户，未解决工单自动按持单量均分分派。

---

## 功能一览

| 模块 | 功能 |
|------|------|
| 工单管理 | 列表/详情/导入/批量更新/回复客户/内部备注/附件上传/批量归户/手动分派 |
| GitHub 同步 | 拉取 Issue + 评论、按作者自动归户、自动派单、增量更新、手动触发 |
| AI Copilot | 流式思考步骤 + RAG 引用 + 草稿打字输出、采纳/驳回、引用溯源 |
| SLA | 策略矩阵（服务等级 × 优先级）、工作日历/节假日、暂停累计、自动预警/升级 |
| 客户管理 | 客户档案、联系人、归户匹配、GitHub 作者自动建户 |
| 知识库 | 文档上传 → 分片 → 向量化入库、按工单语义自动召回、重建索引 |
| 通知 | 规则引擎（事件 × 接收人 × 通道）、失败任务重试、人工重发 |
| 用户/权限 | 登录、角色（ENGINEER / MANAGER / KB_ADMIN / ADMIN）、租户隔离、自助改密/邮箱 |
| 看板 | 工单状态分布、优先级分布、SLA 达成率、FRT/MTTR |

---

## 目录结构

```
support-pilot/
├── docker-compose.yml        # 全栈编排（mysql + backend + frontend/nginx）
├── .env.example              # 环境变量模板（生产用 .env，已被 gitignore）
├── README.md
├── frontend/                 # React 18 + TS + Vite + Ant Design
│   ├── src/api/              # client.ts（API + SSE + 自动 token 续期）
│   ├── src/pages/            # TicketList / TicketDetail / Board / KnowledgeBase /
│   │                         # CustomerManagement / UserManagement / NotifyRules /
│   │                         # SlaSettings / GitHubSync / Profile
│   ├── Dockerfile            # 多阶段：node 构建 → nginx 运行
│   └── nginx.conf            # SPA 回退 + /api 反代 + SSE 超时
├── backend/                  # Spring Boot 3.x + Java 21
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/supportpilot/
│       │   ├── entity/       # 20+ JPA 实体
│       │   ├── repository/   # Spring Data JPA
│       │   ├── service/      # Ticket / GitHubSync / Mail / Sla / Agent / ...
│       │   ├── controller/
│       │   ├── rag/          # Milvus 向量检索
│       │   └── config/       # AuthFilter / JwtUtil / CORS / Scheduler
│       └── resources/
│           ├── application.yml
│           └── db/
│               ├── schema-mysql.sql    # 幂等建表
│               └── data-mysql.sql      # 可选演示种子
└── docs/
```

---

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | React 18 + TypeScript + Vite 4 + Ant Design 5 + react-router-dom 6 |
| 后端 | Spring Boot 3.x + Java 21（虚拟线程）+ Spring Data JPA + Spring AI + SseEmitter |
| 关系库 | MySQL 8.4 |
| 向量库 | Milvus（远程部署） |
| 向量化 | 阿里云百炼 `text-embedding-v4`（支持任何 OpenAI 兼容接口） |
| 大模型 | DeepSeek / 任何 OpenAI 兼容网关 |
| 鉴权 | jjwt 0.12.6，30 分钟滑动过期 |
| 邮件 | SMTP（QQ / 腾讯企业邮 / Outlook），465 隐式 SSL 或 587 STARTTLS |
| 部署 | Docker Compose + Nginx 反代 |

---

## 快速开始

### 方式一：Docker 一键部署（推荐生产）

```bash
cp .env.example .env          # 把 changeme 全部换成真实值
docker compose up -d --build
docker compose logs -f backend
```

首次启动会自动建库建表（`CREATE TABLE IF NOT EXISTS`，幂等可重复执行），并可选灌入演示种子数据。

- 前端：http://\<服务器IP\>/
- 默认管理员：`admin / 123456`（**登录后立即改密码**）
- 后端不对外暴露 8080，仅由 Nginx 反代；MySQL 3306 同理

### 方式二：本地开发（JDK 21 + MySQL 8）

1. **起数据库**（也可以用 Docker 快速起一个）：
   ```bash
   docker run --name mysql-sp -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=supportpilot \
     -p 3306:3306 -d mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
   ```
2. **起后端**：
   ```bash
   cd backend
   mvn spring-boot:run    # 自动建表 + 可选种子
   ```
3. **起前端**（默认通过 Vite 代理到 3001 mock；要连真实后端设置 `VITE_API_TARGET`）：
   ```bash
   cd frontend
   npm install
   $env:VITE_API_TARGET="http://localhost:8080"   # PowerShell
   npm run dev
   ```

### 方式三：Mock 演示（零依赖体验）

前端 + mock-server（端口 3001），无需 Java / MySQL / 大模型。
```bash
cd mock-server && npm install && npm start
cd ../frontend && npm install && npm run dev
```

---

## 服务器部署清单

1. **Docker + Compose v2**：`docker compose version` 能输出版本即可
2. **开放端口**：只需要 **80**（HTTP）；3306、8080、19530 都不需要放行
3. **能访问 Milvus**：在服务器上执行 `nc -vz 8.136.21.168 19530` 验证；连不上可以临时设 `MILVUS_INIT_SCHEMA=false` 启动（知识库功能不可用）
4. **HTTPS（可选）**：在服务器外层 Nginx / 网关把 80 映射到域名并配证书；`frontend/nginx.conf` 已内置 SPA 回退、SSE 长连接超时、gzip

### 上传部署包（Windows → Linux）

```cmd
tar -czf supportpilot-deploy.tar.gz --exclude=node_modules --exclude=target --exclude=dist --exclude=.git docker-compose.yml .env backend frontend
scp supportpilot-deploy.tar.gz admin@<服务器IP>:~/
```

```bash
mkdir -p ~/supportpilot
tar -xzf ~/supportpilot-deploy.tar.gz -C ~/supportpilot
cd ~/supportpilot
chmod 600 .env
docker compose up -d --build
docker compose logs -f backend
```

### 常用运维

| 操作 | 命令 |
|------|------|
| 看后端日志 | `docker compose logs -f backend` |
| 重启（改 .env 后） | `docker compose restart backend` |
| 停止（数据保留） | `docker compose down` |
| 彻底清理 | `docker compose down -v` |
| 导出 DB 备份 | `docker exec supportpilot-mysql mysqldump -uroot -p"$DB_ROOT_PASSWORD" supportpilot > backup.sql` |
| 手动触发 GitHub 同步 | `curl http://localhost:8080/api/github-sync/run -X POST` |

---

## 环境变量

完整列表见 [.env.example](.env.example)。关键项：

### 数据库
| 变量 | 默认 | 说明 |
|------|------|------|
| `DB_HOST` / `DB_PORT` | mysql / 3306 | 容器编排里填 `mysql`（服务名） |
| `DB_NAME` / `DB_USER` | supportpilot / supportpilot | 应用账号 |
| `DB_PASSWORD` | — | **必须改**，首次初始化后不要再动 |
| `DB_ROOT_PASSWORD` | — | root 密码，用于备份/运维 |
| `SQL_INIT_DATA_LOCATIONS` | classpath:db/data-mysql.sql | 留空字符串 = 不导入演示数据 |

### 鉴权
| 变量 | 默认 | 说明 |
|------|------|------|
| `JWT_SECRET` | — | **必须改**为 64 位随机串，否则 token 可伪造 |
| `TOKEN_EXPIRE_MINUTES` | 30 | 无操作自动退出，有操作滑动续期 |
| `DEFAULT_TENANT_ID` | 1 | 无 JWT 时的默认租户 |
| `SECURITY_PERMIT_ALL` | /api/auth/login,/actuator/** | 公网部署不要包含 /api/auth/register |

### 大模型
| 变量 | 默认 | 说明 |
|------|------|------|
| `AI_API_KEY` | — | 对话模型 API Key |
| `AI_BASE_URL` | https://api.deepseek.com | 任何 OpenAI 兼容网关 |
| `AI_MODEL` | deepseek-flash | 对话模型名 |
| `AI_EMBEDDING_API_KEY` / `BASE_URL` / `MODEL` | — | 向量化模型（可与对话用同一套） |

### 向量库
| 变量 | 默认 | 说明 |
|------|------|------|
| `MILVUS_HOST` / `PORT` | 8.136.21.168 / 19530 | 远程 Milvus 地址 |
| `MILVUS_INIT_SCHEMA` | true | 首次启动时自动建 collection + index |

### 邮件（可选，不配则降级为仅站内信）
| 变量 | 示例 | 说明 |
|------|------|------|
| `MAIL_HOST` / `PORT` | smtp.qq.com / 465 | QQ 465 SSL；Outlook 用 587 STARTTLS |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 你的邮箱 / 授权码 | **授权码不是登录密码** |
| `MAIL_SMTP_SSL` / `MAIL_SMTP_STARTTLS` | true / false | 与端口匹配 |
| `MAIL_FROM` | 你的邮箱 | 一般要与用户名一致 |
| `MAIL_DOMAIN` | qq.com | 用户无邮箱字段时回退「登录名@域名」 |

### GitHub 同步（可选）
| 变量 | 默认 | 说明 |
|------|------|------|
| `GITHUB_SYNC_ENABLED` | false | 打开前先配 Owner/Repo/Token |
| `GITHUB_OWNER` / `REPO` | — | 仓库 |
| `GITHUB_TOKEN` | — | Personal Access Token，至少需要 repo 读取权限 |
| `GITHUB_MAX_ISSUES` | 20 | 单次拉取上限，最多 100 |

### SLA
| 变量 | 默认 | 说明 |
|------|------|------|
| `SLA_SCAN_CRON` | 0 */5 * * * ? | 每 5 分钟扫描 |
| `SLA_AUTO_ESCALATE_ENABLED` | true | 超时违约满 N 分钟自动升级 |
| `SLA_AUTO_ESCALATE_DELAY_MINUTES` | 30 | 违约多久后升级 |

---

## API 概览

统一返回体 `{ "code": 0, "message": "success", "data": ... }`，`code != 0` 表示失败。

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户名密码登录，返回 JWT |
| POST | `/api/auth/register` | 自助注册（公网部署默认关闭） |

### 工单
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tickets` | 分页列表，支持 status/priority/customerId/keyword 过滤 |
| GET | `/api/tickets/{id}` | 详情（含会话消息、联系人、SLA 快照） |
| POST | `/api/tickets` | 新建 |
| POST | `/api/tickets/import` | 批量导入（幂等） |
| POST | `/api/tickets/{id}/assign` | 分派/转派 |
| POST | `/api/tickets/{id}/status` | 状态变更 |
| POST | `/api/tickets/{id}/message` | 回复（`internal=true` 为内部备注；非内部会触发客户邮件） |

### GitHub 同步
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/github-sync/run` | 立即触发一次同步 |
| GET | `/api/github-sync/config` | 查看/修改同步配置 |
| GET | `/api/github-sync/status` | 最近同步结果 |

### SSE（AI Copilot）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/runs?ticketId=` | **SSE 流**：thinking / step / draft / cites / done |

SSE 事件载荷：
- `thinking`：`{ "label": "..." }`
- `step`：`{ "tag", "cls", "time", "html" }`
- `draft`：`{ "content", "cost" }`
- `cites`：`{ "items": [...] }`
- `done`：`{ "ticketId" }`

### 其他
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/metrics/overview` | 看板指标 |
| GET | `/api/sla/policies` | SLA 策略矩阵 |
| GET | `/api/customers` | 客户列表 |
| GET | `/api/users` | 用户管理（MANAGER 以上） |
| GET | `/api/notify-rules` | 通知规则 |

响应头 `X-Refreshed-Token`：token 过期前 1/3 生命周期内的请求会刷新 token，前端自动存储。

---

## 部署后检查清单

- [ ] 用 `admin / 123456` 登录后立即改密码
- [ ] 在「用户管理」给每个处理人填真实邮箱（否则 QQ 邮箱回退地址收不到）
- [ ] 测试邮件：新建一条通知规则 → 分派一个工单 → 查收件箱
- [ ] 验证 GitHub 同步：填好 Owner/Repo/Token 后在「GitHub 同步」页点「立即同步」
- [ ] 验证 SLA：等 5 分钟看扫描日志，或手动改一个测试工单的 SLA 快照触发预警
- [ ] `nc -vz 8.136.21.168 19530` 验证 Milvus 可达性
- [ ] 生产环境把 JWT_SECRET、DB_PASSWORD、MAIL_PASSWORD 全部轮换一次

---

## 安全说明

- **密钥隔离**：`.env`、`application-secret.yml`、`target/`、`node_modules/`、`.m2-repo/` 全部被 `.gitignore` 排除，不会进入 Git 仓库
- **公网注册**：`SECURITY_PERMIT_ALL` 默认不含 `/api/auth/register`，公网部署任何人无法自助注册
- **邮件授权码**：SMTP 密码用的是邮箱厂商授权码，而非登录密码，泄露范围可控
- **数据库端口**：编排内 MySQL 不对外暴露，仅后端容器能连
- **JWT**：30 分钟滑动过期 + 每次签发新密钥会自动让旧 token 失效（单密钥轮换场景）
- **建议**：服务器只开放 80 端口 + 密钥登录；生产环境启用 HTTPS