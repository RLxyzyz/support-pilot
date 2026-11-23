# SupportPilot 智能工单系统

面向技术支持团队的 AI 工单处理系统：工单列表 / 详情、AI Copilot 流式生成回复草稿、知识库管理、运营看板。前后端分离，可一键 Docker 部署到服务器。

![架构](https://img.shields.io/badge/arch-Frontend%20%2B%20Backend%20%2B%20PostgreSQL-blue) ![Java](https://img.shields.io/badge/Java-21-orange) ![React](https://img.shields.io/badge/React-18-61dafb)

---

## 目录

- [架构总览](#架构总览)
- [目录结构](#目录结构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
  - [方式一：Mock 演示（零依赖，本机立即体验）](#方式一mock-演示零依赖本机立即体验)
  - [方式二：本地运行真实后端（需 JDK 21 + PostgreSQL）](#方式二本地运行真实后端需-jdk-21--postgresql)
  - [方式三：Docker 一键部署（推荐服务器部署）](#方式三docker-一键部署推荐服务器部署)
- [服务器部署步骤](#服务器部署步骤)
- [环境变量](#环境变量)
- [前端构建与 Nginx 部署](#前端构建与-nginx-部署)
- [后端构建](#后端构建)
- [接入真实大模型](#接入真实大模型)
- [API 契约](#api-契约)
- [后续迭代路线](#后续迭代路线)

---

## 架构总览

```
┌──────────────────────────────────────────────────────────┐
│                      浏览器 / Nginx :80                    │
│   前端 React (dist 静态资源)   │   /api 反代                │
└───────────────┬───────────────────────┬──────────────────┘
                │                       │
                ▼                       ▼
        ┌───────────────┐      ┌─────────────────┐
        │  React 前端    │      │ Spring Boot :8080│
        │  (Vite 构建)   │◄────►│  Controller/Service│
        └───────────────┘      └────────┬─────────┘
                                        │ JPA
                                        ▼
                              ┌────────────────────┐
                              │ PostgreSQL + pgvector│
                              │ (11 业务表 + 向量表)  │
                              └────────────────────┘
                                        │
                          ┌─────────────┴─────────────┐
                          ▼                           ▼
                  AI Mock 场景（默认）          真实大模型 + RAG
                  （无需 Key 开箱即用）        （AI_MOCK_ENABLED=false）
```

- **前端** 与 **mock-server / 后端** 共用同一套 API 契约，前端零改动即可在 mock 与真实后端之间切换。
- AI 默认走 **mock 演示场景**（与原型 1:1），保证无模型 Key、无向量库时全流程可跑；配置后切换真实大模型。

---

## 目录结构

```
support-pilot/
├── docker-compose.yml          # 全栈一键编排（postgres + backend + frontend/nginx）
├── .env.example                # 环境变量模板
├── README.md
├── frontend/                   # React 18 + TS + Vite 前端
│   ├── src/
│   │   ├── api/client.ts       # API 客户端 + SSE 订阅
│   │   ├── components/         # AIPanel（流式 AI 面板）、Toast
│   │   ├── layouts/Shell.tsx   # 整体布局
│   │   ├── pages/              # TicketList / TicketDetail / Board / KnowledgeBase
│   │   └── styles/global.css   # 原型样式 1:1 移植
│   ├── Dockerfile              # 多阶段：node 构建 → nginx 运行
│   ├── nginx.conf              # 静态托管 + SPA 回退 + /api 反代 + SSE 配置
│   └── vite.config.ts          # dev 代理 /api → VITE_API_TARGET
├── backend/                    # Spring Boot 3.3 后端
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-compose.yml      # 仅后端 + postgres（本地后端开发用）
│   └── src/main/
│       ├── java/com/supportpilot/
│       │   ├── SupportPilotApplication.java
│       │   ├── common/         # Result / 异常 / 多租户 / JWT / 过滤器
│       │   ├── config/         # CORS
│       │   ├── entity/         # 11 个 JPA 实体
│       │   ├── repository/     # 11 个 Repository
│       │   ├── dto/            # 与前端对齐的 VO
│       │   ├── service/        # Ticket / Agent(SSE) / Draft / Kb / Metrics
│       │   ├── controller/     # 5 个 Controller
│       │   └── rag/            # RAG 接口 + Mock/Real 实现
│       └── resources/
│           ├── application.yml / application-prod.yml
│           ├── db/schema.sql   # 建表（pgvector 扩展 + 11 表 + 向量表）
│           ├── db/data.sql     # 演示种子数据
│           └── prompts/triage.txt
└── mock-server/                # 轻量 Node mock 后端（本机无 Java 时联调用）
    ├── server.js
    └── data.js
```

---

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | React 18 + TypeScript + Vite 4 + react-router-dom 6（不引 UI 库，原型样式 1:1 移植） |
| 后端 | Spring Boot 3.3.5 + Java 21 + Spring Data JPA + Spring AI 1.0.0 + SseEmitter（虚拟线程） |
| 数据库 | PostgreSQL 16 + pgvector |
| 鉴权 | jjwt 0.12.6（轻量多租户解析，完整鉴权留待 S3） |
| 部署 | Docker Compose + Nginx 反代 |

---

## 快速开始

### 方式一：Mock 演示（零依赖，本机立即体验）

无需 Java / PostgreSQL，前端通过 Vite 代理访问 mock-server。

```bash
# 1. 启动 mock 后端（端口 3001）
cd mock-server
npm install
npm start        # node server.js

# 2. 启动前端 dev server（端口 5173）
cd ../frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173 ，体验 4 个页面与 AI 流式生成全流程。

> npm 缓存若因中文路径报错，可指定纯 ASCII 缓存目录：
> `npm install --cache "D:\npm-cache-sp"`

### 方式二：本地运行真实后端（需 JDK 21 + PostgreSQL）

1. 准备 PostgreSQL 16 并安装 pgvector 扩展（或用 `backend/docker-compose.yml` 起一个带 pgvector 的容器）：
   ```bash
   cd backend
   docker compose up -d postgres   # 仅启动数据库
   ```
2. 启动后端（默认 AI mock，自动建表 + 灌种子数据）：
   ```bash
   cd backend
   mvn clean spring-boot:run
   ```
3. 前端切到真实后端：
   ```bash
   cd frontend
   $env:VITE_API_TARGET="http://localhost:8080"   # PowerShell
   npm run dev
   ```

### 方式三：Docker 一键部署（推荐服务器部署）

```bash
cd support-pilot
cp .env.example .env     # 按需修改
docker compose up -d --build
```

启动后：
- 前端：http://\<host\>/
- 后端健康检查：http://\<host\>:8080/actuator/health

查看日志：`docker compose logs -f backend`

---

## 服务器部署步骤

以 Linux 服务器为例：

1. **安装 Docker 与 Docker Compose**（略）。
2. **上传工程**到服务器，例如 `/opt/support-pilot`。
3. **配置环境变量**：
   ```bash
   cd /opt/support-pilot
   cp .env.example .env
   vim .env   # 至少修改 DB_PASSWORD、JWT_SECRET；接入真实模型时填 AI_API_KEY
   ```
4. **构建并启动**：
   ```bash
   docker compose up -d --build
   ```
5. **配置反向代理 / HTTPS**（可选）：在服务器外层 Nginx / 网关把 80 端口映射到域名，并配置 SSL 证书。`frontend/nginx.conf` 已内置 SPA 回退与 `/api` 反代，外层只需透传。
6. **验证**：
   - 访问前端页面，确认工单列表 7 条数据渲染正常。
   - 进入工单详情 → 「交给 AI 处理」，确认 SSE 流式步骤 / 草稿打字 / 引用正常。
   - 点击「采纳并回复客户」，确认工单变「已解决」、绿色 toast 弹出、刷新后状态保持。

数据持久化：PostgreSQL 数据存在 Docker 命名卷 `pgdata`，`docker compose down` 不会删除；彻底清理用 `docker compose down -v`。

---

## 环境变量

完整列表见 [.env.example](.env.example)。关键项：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | localhost:5432/supportpilot/postgres/postgres | 数据库连接 |
| `SERVER_PORT` | 8080 | 后端端口 |
| `DEFAULT_TENANT_ID` | 1 | 无 JWT 时的默认租户 |
| `JWT_SECRET` | supportpilot-dev-secret-change-me | JWT 签名密钥，**生产务必修改** |
| `AI_MOCK_ENABLED` | true | true=演示场景；false=真实大模型 |
| `AI_API_KEY` | （空） | OpenAI 兼容接口 Key |
| `AI_BASE_URL` | https://dashscope.aliyuncs.com/compatible-mode/v1 | 模型网关地址 |
| `AI_MODEL` | qwen-max | 模型名 |
| `VITE_API_TARGET` | http://localhost:3001 | 仅前端 dev 代理用 |

---

## 前端构建与 Nginx 部署

```bash
cd frontend
npm install
npm run build       # 产出 dist/（tsc 类型检查 + vite 构建）
```

将 `dist/` 上传到服务器，用 `frontend/nginx.conf` 作为 Nginx 站点配置：
- 静态资源根目录指向 `dist/`
- `try_files $uri $uri/ /index.html` 处理 SPA 路由
- `location /api/` 反代到后端 `http://backend:8080`，并关闭缓冲以支持 SSE

> 若部署在子路径（如 https://domain/support/），构建时设置 `VITE_BASE=/support/`，并在 Nginx 调整 `try_files`。

---

## 后端构建

```bash
cd backend
mvn clean package -DskipTests     # 产出 target/support-pilot-backend.jar
```

运行：
```bash
java -jar target/support-pilot-backend.jar                      # 默认 profile
java -jar -Dspring.profiles.active=prod target/support-pilot-backend.jar   # 生产（不自动建表 / 灌数据）
```

生产环境建议关闭 `spring.sql.init`（`application-prod.yml` 已设 `mode: never`），改用 Flyway/Liquibase 管理表结构迁移。

---

## 接入真实大模型

1. 在 `.env` 中设置：
   ```
   AI_MOCK_ENABLED=false
   AI_API_KEY=sk-xxxx
   AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1   # 或你的 OpenAI 兼容网关
   AI_MODEL=qwen-max
   ```
2. `docker compose up -d --build backend` 重启后端。
3. Agent 会尝试调用真实模型；若调用失败（Key 无效 / 网络不通），自动回退到演示场景，保证页面可用。

向量检索（RAG）当前为骨架（`RealRagService`），S2 将接入 Spring AI pgvector `VectorStore`，对 `kb_chunk` 表做相似度检索。

---

## API 契约

统一返回体：`{ "code": 0, "message": "success", "data": ... }`，`code != 0` 表示失败。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tickets?status=&priority=&keyword=&page=&size=` | 工单分页列表 |
| GET | `/api/tickets/{id}` | 工单详情（含会话消息） |
| POST | `/api/tickets` | 新建工单 |
| POST | `/api/tickets/import` | 批量导入（幂等） |
| GET | `/api/kb/documents` | 知识库文档列表 |
| POST | `/api/kb/reindex` | 触发重建索引 |
| POST | `/api/kb/search` | 知识库检索 |
| GET | `/api/metrics/overview` | 看板指标 |
| GET | `/api/agent/runs?ticketId=` | **SSE 流**：thinking/step/draft/cites/done |
| POST | `/api/drafts/{id}/approve` | 采纳草稿 → 工单状态变「已解决」 |
| POST | `/api/drafts/{id}/reject` | 驳回草稿 |

SSE 事件载荷：
- `thinking`：`{ "label": "..." }`
- `step`：`{ "tag", "cls", "time", "html" }`
- `draft`：`{ "content", "cost" }`
- `cites`：`{ "items": [...] }`
- `done`：`{ "ticketId" }`

---

## 后续迭代路线

- **S1（已完成）**：前后端分离工程、4 页面、AI 流式演示、Docker 部署。
- **S2**：接入真实 RAG（pgvector 向量检索）、文档分片入库、引用可溯源。
- **S3**：完整鉴权（登录 / 签发 JWT / RBAC）、多租户数据强隔离、审计日志。
- **S4**：评测闭环（采纳 / 驳回回流为评测样本）、A/B 看板、成本归因。
