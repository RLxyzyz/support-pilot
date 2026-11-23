// 演示数据，与原型 index.html 保持一致，前后端通用
const tickets = [
  {
    id: '#41234',
    title: 'Health endpoint returns 503 after upgrading to 3.3.0',
    tenant: 'Acme 金融',
    product: 'Spring Boot 3.3.0',
    priority: 'P1',
    priorityClass: 'p1',
    status: '处理中',
    statusClass: 'st-prog',
    category: '配置 / 健康检查',
    sla: '剩余 42 分钟',
    slaPct: 38,
    slaColor: '#dc2626',
    updateTime: '3 分钟前',
    body: '升级到 Spring Boot 3.3.0 后，K8s 的 readiness / liveness 探针全部返回 503，Pod 一直处于 NotReady 状态，滚动更新卡死。回滚到 3.2.7 后恢复正常。\n\n已确认 /actuator/health 匿名访问可通，但 /actuator/health/readiness 返回 503。',
    code: 'management:\n  endpoints:\n    web:\n      exposure:\n        include: health\n  endpoint:\n    health:\n      show-details: always',
    messages: [
      { who: '张明', role: '客户 · Acme 金融', text: '补充一下，我们是 12 节点的生产集群，用的是 K8s 1.28，探针配置没改过。', internal: false },
      { who: '李维', role: 'L2 支持工程师', text: '收到，正在核对 3.3 版本 Actuator 探针的默认行为变化。', internal: false },
      { who: '李维', role: '内部备注', text: '怀疑是 3.3 起 probes 需要显式开启，且 readiness group 默认不含自定义 HealthIndicator。', internal: true }
    ]
  },
  {
    id: '#41230',
    title: 'Application fails to start: BindException port 8080 already in use',
    tenant: 'Bluepeak 物流',
    product: 'Spring Boot 3.2.7',
    priority: 'P2',
    priorityClass: 'p2',
    status: '待分诊',
    statusClass: 'st-new',
    category: '环境 / 启动失败',
    sla: '剩余 51 分钟',
    slaPct: 74,
    slaColor: '#d97706',
    updateTime: '12 分钟前',
    body: '本地启动时报端口占用，但 netstat 看不到 8080 被监听。已试过重启 IDE 无效。\n换了 8081 可以启动，但我们的前端写死了 8080。',
    code: '***************************\nAPPLICATION FAILED TO START\n***************************\n\nDescription:\nWeb server failed to start. Port 8080 was already in use.',
    messages: [
      { who: '王倩', role: '客户 · Bluepeak 物流', text: '补充：机器是 M1 Mac，之前装过一个本地代理软件。', internal: false }
    ]
  },
  {
    id: '#41228',
    title: 'How to customize the startup banner text',
    tenant: 'Nova 教育',
    product: 'Spring Boot 3.2.7',
    priority: 'P4',
    priorityClass: 'p4',
    status: '已解决',
    statusClass: 'st-res',
    category: '用法咨询',
    sla: '已完成',
    slaPct: 100,
    slaColor: '#cbd5e1',
    updateTime: '1 小时前',
    body: '想把启动时打印的 Spring 字样换成公司 logo，是否支持自定义？需要保留版本号输出。',
    code: '',
    messages: [
      { who: '陈璐', role: 'L2 支持工程师', text: '在 src/main/resources 下放置 banner.txt 即可，占位符 ${spring-boot.version} 会替换为版本号。已附示例。', internal: false }
    ]
  },
  {
    id: '#41221',
    title: 'Micrometer metrics missing after adding actuator dependency',
    tenant: 'Acme 金融',
    product: 'Spring Boot 3.3.0',
    priority: 'P2',
    priorityClass: 'p2',
    status: '待客户',
    statusClass: 'st-wait',
    category: '可观测性',
    sla: '已暂停',
    slaPct: 55,
    slaColor: '#94a3b8',
    updateTime: '2 小时前',
    body: '加了 spring-boot-starter-actuator 之后 /actuator/metrics 只有 jvm 相关指标，没有 http.server.requests。',
    code: '',
    messages: [
      { who: '李维', role: 'L2 支持工程师', text: '请确认是否引入了 micrometer-registry-prometheus，以及 management.metrics.web.server.request.autotime 配置。方便贴一下 pom 片段吗？', internal: false }
    ]
  },
  {
    id: '#41215',
    title: 'Native image build fails with GraalVM 22.3 and Spring Boot 3.3',
    tenant: 'Vertex 医疗',
    product: 'Spring Boot 3.3.0',
    priority: 'P1',
    priorityClass: 'p1',
    status: '已升级',
    statusClass: 'st-esc',
    category: '原生镜像 / 构建',
    sla: '已升级 L2 · 重算 3h58m',
    slaPct: 92,
    slaColor: '#dc2626',
    updateTime: '3 小时前',
    body: '使用 native-maven-plugin 构建时在 reflection 阶段失败，日志显示缺少 JNI 配置。已在 3.2.x 上验证通过。',
    code: 'Error: Classes that should be initialized at run time got initialized during image building',
    messages: [
      { who: '系统', role: '自动升级', text: 'P1 工单 15 分钟未首响，已自动升级至 L2 并通知值班。', internal: true }
    ]
  },
  {
    id: '#41209',
    title: 'Kafka consumer rebalance loop with batch listener enabled',
    tenant: 'Bluepeak 物流',
    product: 'Spring Kafka 3.2',
    priority: 'P3',
    priorityClass: 'p3',
    status: '处理中',
    statusClass: 'st-prog',
    category: '消息 / 消费',
    sla: '剩余 2.1 天',
    slaPct: 62,
    slaColor: '#059669',
    updateTime: '5 小时前',
    body: '开启批量监听后消费组每 30 秒触发一次 rebalance，消费停滞。单条模式正常。',
    code: '',
    messages: []
  },
  {
    id: '#41201',
    title: 'CORS preflight blocked in WebFlux after security config change',
    tenant: 'Nova 教育',
    product: 'Spring Boot 3.2.7',
    priority: 'P3',
    priorityClass: 'p3',
    status: '待分诊',
    statusClass: 'st-new',
    category: '安全 / WebFlux',
    sla: '剩余 3.4 小时',
    slaPct: 85,
    slaColor: '#d97706',
    updateTime: '6 小时前',
    body: '调整 SecurityWebFilterChain 后，浏览器预检请求返回 403，GET 正常。',
    code: '',
    messages: []
  }
];

const kbDocs = [
  { name: 'Actuator Health 健康检查', path: 'docs/actuator/health.adoc', productLine: 'Spring Boot 3.3', chunks: 64, hits: 1284, status: '已索引' },
  { name: 'Actuator Endpoints 端点暴露', path: 'docs/actuator/endpoints.adoc', productLine: 'Spring Boot 3.3', chunks: 58, hits: 962, status: '已索引' },
  { name: 'Web Server 端口与绑定', path: 'docs/web/server.adoc', productLine: 'Spring Boot 3.3', chunks: 41, hits: 733, status: '已索引' },
  { name: 'Metrics 指标采集', path: 'docs/actuator/metrics.adoc', productLine: 'Spring Boot 3.3', chunks: 77, hits: 601, status: '已索引' },
  { name: 'Native Image 原生镜像', path: 'docs/native-image/intro.adoc', productLine: 'Spring Boot 3.3', chunks: 96, hits: 418, status: '待重建' },
  { name: 'Kafka 批量监听配置', path: 'docs/messaging/kafka.adoc', productLine: 'Spring Kafka 3.2', chunks: 52, hits: 287, status: '已索引' },
  { name: 'WebFlux 安全配置', path: 'docs/web/reactive-security.adoc', productLine: 'Spring Boot 3.3', chunks: 38, hits: 245, status: '已索引' },
  { name: '历史工单沉淀（自动）', path: 'kb/auto-generated', productLine: '全部', chunks: 2180, hits: 3096, status: '审核中' }
];

const misses = [
  { question: 'GraalVM 22.3 与 3.3 的反射配置差异', count: 37, weight: 100 },
  { question: 'K8s 探针 readiness group 自定义', count: 29, weight: 78 },
  { question: '批量监听 rebalance 超时参数', count: 24, weight: 65 },
  { question: 'WebFlux 预检请求 403 排查', count: 18, weight: 49 },
  { question: '多数据源事务传播行为', count: 11, weight: 30 }
];

const board = {
  kpi: [
    { label: '平均处理时长 AHT', value: '11.4', unit: '分钟', delta: '↓ 48.2% 较上月', trend: 'up' },
    { label: '草稿采纳率', value: '67.8', unit: '%', delta: '↑ 6.1pt 较上周', trend: 'up' },
    { label: '首次响应达标率', value: '96.3', unit: '%', delta: '↑ 8.3pt 较上月', trend: 'up' },
    { label: '单工单 AI 成本', value: '¥0.031', unit: '', delta: '↓ 38% 较上月', trend: 'up' }
  ],
  trend: [
    { day: '周三', value: 22 },
    { day: '周四', value: 20 },
    { day: '周五', value: 17 },
    { day: '周六', value: 14 },
    { day: '周日', value: 13 },
    { day: '周一', value: 12.2 },
    { day: '今天', value: 11.4 }
  ],
  adoption: {
    rate: 67.8,
    items: [
      { label: '直接采纳（未修改）', pct: 38.2, color: '#2563eb' },
      { label: '修改后采纳', pct: 29.6, color: '#93c5fd' },
      { label: '驳回', pct: 32.2, color: '#e2e8f0' }
    ]
  }
};

// Agent 演示场景（与原型 runAgent 一致），SSE 推流用
const agentScenario = {
  labels: [
    '正在读取工单与会话记录…',
    '正在检索官方文档与历史工单…',
    '正在调用客户信息服务…',
    '正在校验信息完整性…',
    '正在生成回复草稿…'
  ],
  steps: [
    {
      tag: '分诊', cls: 'tag-triage', time: '0.8s',
      html: '问题类型 <b>配置类</b>（Actuator 健康检查）· 建议优先级 <b>P1</b> · 置信度 <b>0.91</b><br><span style="color:var(--muted)">依据：升级后探针全 503 且 Pod 不可就绪，生产集群全站不可用；回滚即恢复，指向版本行为变更。</span>'
    },
    {
      tag: '检索', cls: 'tag-retrieve', time: '1.3s',
      html: '命中 <b>3</b> 条依据（混合检索 + 精排）<div style="margin-top:6px"><div class="hit"><span class="hit-idx">1</span><span class="hit-name">Actuator Health §3.2 探针分组</span><span class="hit-score">0.93</span></div><div class="hit"><span class="hit-idx">2</span><span class="hit-name">历史工单 #39871（同现象，已解决）</span><span class="hit-score">0.88</span></div><div class="hit"><span class="hit-idx">3</span><span class="hit-name">Actuator Endpoints §1.4 暴露配置</span><span class="hit-score">0.81</span></div></div>'
    },
    {
      tag: '工具', cls: 'tag-tool', time: '2.1s',
      html: '<b>get_customer_subscription</b>(tenant=Acme 金融)<br><span style="color:var(--muted)">→ Enterprise 支持 · Spring Boot 3.3.0 · 生产环境 12 节点 · SLA P1 4 小时</span>'
    },
    {
      tag: '校验', cls: 'tag-check', time: '2.4s',
      html: '信息完整性 <b>不足</b>，建议在回复中索取：<br><span style="color:var(--muted)">· 最小复现的 application.yml 片段　· Deployment 中 probe 配置　· 是否存在自定义 HealthIndicator</span>'
    },
    {
      tag: '生成', cls: 'tag-gen', time: '2.9s',
      html: '已生成回复草稿，含 <b>3</b> 处引用，<b>0</b> 处高风险承诺。'
    }
  ],
  draft: '您好，\n\n根据您提供的版本与现象，判断为 Spring Boot 3.3 升级后 Actuator 探针默认行为变化导致的配置问题，服务本身并未不可用。[1]\n\n建议按以下步骤处理：\n\n1. 确认已显式开启探针：management.endpoint.health.probes.enabled=true（3.3 起需显式声明）。[1]\n2. 检查 K8s 探针路径是否指向 /actuator/health/liveness 与 /actuator/health/readiness，而非 /actuator/health。[1]\n3. 若存在自定义 HealthIndicator，请确认其未因依赖未就绪而抛出异常，导致 readiness group 整体 DOWN。[2]\n4. 如需保留原有聚合健康检查语义，可为 readiness group 单独配置 include。[3]\n\n适用版本：Spring Boot 3.3.x 及以上。\n\n为便于进一步确认，麻烦补充：最小复现的 application.yml 片段、Deployment 中的 probe 配置，以及是否存在自定义 HealthIndicator。\n\n参考：\n[1] Actuator Health §3.2 探针分组\n[2] 历史工单 #39871（同现象已解决）\n[3] Actuator Endpoints §1.4 暴露配置',
  cites: [
    'Actuator Health §3.2 探针分组 · docs/actuator/health.adoc',
    '历史工单 #39871 · 相似度 0.88',
    'Actuator Endpoints §1.4 暴露配置 · docs/actuator/endpoints.adoc'
  ],
  cost: '12.4s · 3,482 tok · ¥0.028'
};

module.exports = { tickets, kbDocs, misses, board, agentScenario };
