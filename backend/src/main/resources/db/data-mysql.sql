-- SupportPilot 演示种子数据（MySQL 版本，与 mock-server / 原型一致，重启幂等）
-- 长文本使用单引号包裹，保留换行；ON CONFLICT 改为 INSERT IGNORE。

SET NAMES utf8mb4;

-- 租户
INSERT IGNORE INTO tenant (id, name, code) VALUES
 (1, 'Acme 金融', 'acme'),
 (2, 'Bluepeak 物流', 'bluepeak'),
 (3, 'Nova 教育', 'nova'),
 (4, 'Vertex 医疗', 'vertex');

-- 产品
INSERT IGNORE INTO product (id, tenant_id, name) VALUES
 (1, 1, 'Spring Boot 3.3.0'),
 (2, 1, 'Spring Boot 3.2.7'),
 (3, 2, 'Spring Kafka 3.2');

-- 工单（id 与原型一致，VO 层自动补 # 前缀；env 为部署环境描述，用于详情 meta 展示）
INSERT IGNORE INTO ticket (id, tenant_id, product_id, title, body, code, priority, priority_class, status, status_class, category, sla, sla_pct, sla_color, update_time, env) VALUES
 (41234, 1, 1,
  'Health endpoint returns 503 after upgrading to 3.3.0',
  '升级到 Spring Boot 3.3.0 后，K8s 的 readiness / liveness 探针全部返回 503，Pod 一直处于 NotReady 状态，滚动更新卡死。回滚到 3.2.7 后恢复正常。

已确认 /actuator/health 匿名访问可通，但 /actuator/health/readiness 返回 503。',
  'management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always',
  'P1', 'p1', '处理中', 'st-prog', '配置 / 健康检查', '剩余 42 分钟', 38, '#dc2626', '3 分钟前', '生产 · K8s 12 节点'),

 (41230, 2, 2,
  'Application fails to start: BindException port 8080 already in use',
  '本地启动时报端口占用，但 netstat 看不到 8080 被监听。已试过重启 IDE 无效。
换了 8081 可以启动，但我们的前端写死了 8080。',
  '***************************
APPLICATION FAILED TO START
***************************

Description:
Web server failed to start. Port 8080 was already in use.',
  'P2', 'p2', '待分诊', 'st-new', '环境 / 启动失败', '剩余 51 分钟', 74, '#d97706', '12 分钟前', '测试 · 本地 M1 Mac'),

 (41228, 3, 2,
  'How to customize the startup banner text',
  '想把启动时打印的 Spring 字样换成公司 logo，是否支持自定义？需要保留版本号输出。',
  '',
  'P4', 'p4', '已解决', 'st-res', '用法咨询', '已完成', 100, '#cbd5e1', '1 小时前', '开发 · 单机'),

 (41221, 1, 1,
  'Micrometer metrics missing after adding actuator dependency',
  '加了 spring-boot-starter-actuator 之后 /actuator/metrics 只有 jvm 相关指标，没有 http.server.requests。',
  '',
  'P2', 'p2', '待客户', 'st-wait', '可观测性', '已暂停', 55, '#94a3b8', '2 小时前', '预发 · 4 节点'),

 (41215, 4, 1,
  'Native image build fails with GraalVM 22.3 and Spring Boot 3.3',
  '使用 native-maven-plugin 构建时在 reflection 阶段失败，日志显示缺少 JNI 配置。已在 3.2.x 上验证通过。',
  'Error: Classes that should be initialized at run time got initialized during image building',
  'P1', 'p1', '已升级', 'st-esc', '原生镜像 / 构建', '已升级 L2 · 重算 3h58m', 92, '#dc2626', '3 小时前', '生产构建流水线'),

 (41209, 2, 3,
  'Kafka consumer rebalance loop with batch listener enabled',
  '开启批量监听后消费组每 30 秒触发一次 rebalance，消费停滞。单条模式正常。',
  '',
  'P3', 'p3', '处理中', 'st-prog', '消息 / 消费', '剩余 2.1 天', 62, '#059669', '5 小时前', '生产 · 6 节点'),

 (41201, 3, 2,
  'CORS preflight blocked in WebFlux after security config change',
  '调整 SecurityWebFilterChain 后，浏览器预检请求返回 403，GET 正常。',
  '',
  'P3', 'p3', '待分诊', 'st-new', '安全 / WebFlux', '剩余 3.4 小时', 85, '#d97706', '6 小时前', '测试 · Docker');

-- 工单会话消息
INSERT INTO ticket_message (ticket_id, who, role, text, internal) VALUES
 (41234, '张明', '客户 · Acme 金融', '补充一下，我们是 12 节点的生产集群，用的是 K8s 1.28，探针配置没改过。', 0),
 (41234, '李维', 'L2 支持工程师', '收到，正在核对 3.3 版本 Actuator 探针的默认行为变化。', 0),
 (41234, '李维', '内部备注', '怀疑是 3.3 起 probes 需要显式开启，且 readiness group 默认不含自定义 HealthIndicator。', 1),
 (41230, '王倩', '客户 · Bluepeak 物流', '补充：机器是 M1 Mac，之前装过一个本地代理软件。', 0),
 (41228, '陈璐', 'L2 支持工程师', '在 src/main/resources 下放置 banner.txt 即可，占位符 ${spring-boot.version} 会替换为版本号。已附示例。', 0),
 (41221, '李维', 'L2 支持工程师', '请确认是否引入了 micrometer-registry-prometheus，以及 management.metrics.web.server.request.autotime 配置。方便贴一下 pom 片段吗？', 0),
 (41215, '系统', '自动升级', 'P1 工单 15 分钟未首响，已自动升级至 L2 并通知值班。', 1);

-- 知识库文档
INSERT INTO kb_doc (name, path, product_line, chunks, hits, status) VALUES
 ('Actuator Health 健康检查', 'docs/actuator/health.adoc', 'Spring Boot 3.3', 64, 1284, '已索引'),
 ('Actuator Endpoints 端点暴露', 'docs/actuator/endpoints.adoc', 'Spring Boot 3.3', 58, 962, '已索引'),
 ('Web Server 端口与绑定', 'docs/web/server.adoc', 'Spring Boot 3.3', 41, 733, '已索引'),
 ('Metrics 指标采集', 'docs/actuator/metrics.adoc', 'Spring Boot 3.3', 77, 601, '已索引'),
 ('Native Image 原生镜像', 'docs/native-image/intro.adoc', 'Spring Boot 3.3', 96, 418, '待重建'),
 ('Kafka 批量监听配置', 'docs/messaging/kafka.adoc', 'Spring Kafka 3.2', 52, 287, '已索引'),
 ('WebFlux 安全配置', 'docs/web/reactive-security.adoc', 'Spring Boot 3.3', 38, 245, '已索引'),
 ('历史工单沉淀（自动）', 'kb/auto-generated', '全部', 2180, 3096, '审核中');

-- 知识盲区 / 评测样本（看板 misses）
INSERT INTO eval_case (question, count, weight) VALUES
 ('GraalVM 22.3 与 3.3 的反射配置差异', 37, 100),
 ('K8s 探针 readiness group 自定义', 29, 78),
 ('批量监听 rebalance 超时参数', 24, 65),
 ('WebFlux 预检请求 403 排查', 18, 49),
 ('多数据源事务传播行为', 11, 30);
