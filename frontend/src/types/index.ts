// 与后端 / mock-server 数据结构对齐的类型定义

export interface Attachment {
  id: number;
  ticketId: number;
  messageId?: number;
  fileName: string;
  ext?: string;
  mime?: string;
  sizeBytes: number;
  sizeText: string;
  uploaderId?: number;
  uploaderName?: string;
  visibility: string;
  createdAt?: string;
}

export interface TicketMessage {
  id?: number;
  who: string;
  role: string;
  text: string;
  internal: boolean;
  attachments?: Attachment[];
  /** V2.3 情绪：POSITIVE / NEUTRAL / NEGATIVE */
  sentiment?: string;
  sentimentIntensity?: number;
}

export interface Ticket {
  id: string;
  title: string;
  tenant: string;
  product: string;
  priority: string;
  priorityClass: string;
  status: string;
  statusClass: string;
  category: string;
  sla: string;
  slaPct: number;
  slaColor: string;
  /** V2.1 SLA 快照：NORMAL / WARNING / BREACHED / PAUSED / STOPPED / MET */
  slaState?: string;
  slaSource?: string;
  slaCalendarType?: string;
  slaResolveDueAt?: string;
  slaFirstResponseDueAt?: string;
  updateTime: string;
  assigneeId?: number;
  assigneeName?: string;
  assigneeUsername?: string;
  env?: string;
  createdAt?: string;
  firstResponseAt?: string;
  resolvedAt?: string;
  csatScore?: number;
  csatComment?: string;
  csatAt?: string;
  relations?: RelatedTicket[];
  body: string;
  code?: string;
  messages?: TicketMessage[];
  /** 工单主体附件（未绑定到消息） */
  attachments?: Attachment[];
  // V2.0 EP1 客户 360 信息块（工单详情 / 列表客户列）
  customerId?: number;
  customerName?: string;
  customerCode?: string;
  slaTier?: string;
  contactId?: number;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  contactTitle?: string;
  contactIsPrimary?: boolean;
  // V2.3 智能服务
  similarityGroupId?: number | null;
  similarCount?: number;
  l0State?: string | null;
  l0ReopenCount?: number;
  sentiment?: string | null;
  summary?: string | null;
}

// 关联工单（#4）
export interface RelatedTicket {
  relationId: number;
  id: string;
  title: string;
  status: string;
  relationType: string;
}

export interface TicketPage {
  list: Ticket[];
  total: number;
}

// ===== V2.1 SLA 策略与日历 =====
export interface SlaPolicy {
  id?: number;
  tenantId?: number;
  tier: string;
  priority: string;
  firstResponseMinutes: number;
  resolveMinutes: number;
  calendarType: string;
  calendarId?: number;
  warningThreshold: number;
  enabled: boolean;
}

export interface SlaCalendar {
  id?: number;
  tenantId?: number;
  name: string;
  workdays: string;
  segments: string;
  timezone: string;
  enabled: boolean;
}

export interface SlaHoliday {
  id: number;
  calendarId: number;
  holidayDate: string;
  name?: string;
}

export interface KbDoc {
  id: number;
  name: string;
  path: string;
  productLine: string;
  chunks: number;
  hits: number;
  status: string;
}

export interface KbDocPage {
  list: KbDoc[];
  total: number;
  page: number;
  size: number;
  productLines: string[];
}

export interface KpiItem {
  label: string;
  value: string;
  unit: string;
  delta: string;
  trend: 'up' | 'down';
}

export interface TrendPoint {
  day: string;
  value: number;
}

export interface AdoptionItem {
  label: string;
  pct: number;
  color: string;
}

export interface MissItem {
  question: string;
  count: number;
  weight: number;
}

export interface OpsNameCount {
  name: string;
  count: number;
}

// 看板运营指标（SLA 达成率 / FRT / MTTR / 处理人负载 / 分类分布 / AI 收益）
export interface OpsMetrics {
  slaRate: number;
  frtMinutes: number;
  mttrHours: number;
  aiSavedMinutes: number;
  aiCost: number;
  kbHitRate: number;
  csatAvg: number;
  csatCount: number;
  resolvedCount: number;
  respondedCount: number;
  assigneeLoad: OpsNameCount[];
  categoryDist: OpsNameCount[];
}

// AI 分诊建议
export interface Triage {
  priority: string;
  category: string;
  assigneeId?: number;
  assigneeName?: string;
  reasoning?: string;
}

// 草稿反馈统计
export interface DraftFeedbackReason {
  reason: string;
  count: number;
}
export interface DraftFeedbackStats {
  adopted: number;
  rejected: number;
  edited: number;
  reasons: DraftFeedbackReason[];
}

// 快捷回复模板（#5）
export interface ReplyTemplate {
  id: number;
  name: string;
  content: string;
  category?: string;
  createdAt?: string;
}

export interface BoardData {
  kpi: KpiItem[];
  trend: TrendPoint[];
  adoption: { rate: number; items: AdoptionItem[] };
  misses: MissItem[];
  ops?: OpsMetrics;
}

// 用户与鉴权
export interface UserInfo {
  id: number;
  username: string;
  name: string;
  /** 通知邮件地址；为空时回退「登录名@mail-domain」 */
  email?: string;
  role: string;
  tenantId?: number;
  /** V2.0 EP2：所属工作组 id */
  teamId?: number;
  /** V2.0 EP2：数据范围 SELF / TEAM / ALL */
  dataScope?: string;
}

// V2.0 EP2 工作组
export interface Team {
  id: number;
  name: string;
  /** active / disabled */
  status: string;
  memberCount: number;
  createdAt?: string;
}

// V2.0 EP3 站内信
export interface AppNotification {
  id: number;
  type: string;
  typeLabel?: string;
  title: string;
  content?: string;
  refType?: string;
  refId?: string;
  isRead: boolean;
  readAt?: string;
  createdAt?: string;
}

export interface NotificationPage {
  list: AppNotification[];
  total: number;
}

// V2.0 EP3 通知规则
export interface NotifyRule {
  id: number;
  tenantId?: number;
  eventType: string;
  eventLabel?: string;
  name?: string;
  conditionJson?: string;
  receivers: string;
  inAppEnabled: boolean;
  emailEnabled: boolean;
  enabled: boolean;
  createdAt?: string;
}

// V2.0 EP3 邮件任务
export interface MailTask {
  id: number;
  notificationId?: number;
  toAddr: string;
  subject: string;
  /** pending / sent / failed */
  status: string;
  retryCount: number;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LoginResponse {
  token: string;
  user: UserInfo;
}

// 工单快速统计（侧边栏角标 / 列表 sub 文案）
export interface TicketQuickStats {
  total: number;
  p1Open: number;
  processing: number;
  pending: number;
}

// 基础数据下拉选项（新建工单选客户 / 产品）
export interface TenantOption {
  id: number;
  name: string;
  code?: string;
}

export interface ProductOption {
  id: number;
  tenantId: number;
  name: string;
}

// Agent SSE 事件
export interface AgentStep {
  tag: string;
  cls: string;
  time: string;
  html: string;
}

export interface AgentStreamHandlers {
  onThinking?: (label: string) => void;
  onStep?: (step: AgentStep) => void;
  onDraft?: (payload: { content: string; cost: string; draftId?: string }) => void;
  onCites?: (items: string[]) => void;
  onDone?: () => void;
  onError?: (err: Event) => void;
}

// 操作审计日志
export interface OperationLog {
  id: number;
  tenantId?: number;
  operatorId?: number;
  operatorName?: string;
  operatorUsername?: string;
  module: string;
  action: string;
  targetType?: string;
  targetId?: string;
  summary?: string;
  result: string; // success / fail
  errorMsg?: string;
  ip?: string;
  userAgent?: string;
  costMs?: number;
  createdAt: string;
  // 以下仅详情接口返回
  beforeData?: string;
  afterData?: string;
  requestParams?: string;
}

export interface OperationLogPage {
  list: OperationLog[];
  total: number;
}

// ===== V2.0 EP1 客户域 =====
export interface Customer {
  id: number;
  tenantId?: number;
  name: string;
  code: string;
  /** 服务等级：金牌 / 银牌 / 铜牌 / 默认 */
  slaTier: string;
  contractNo?: string;
  contractStart?: string;
  contractEnd?: string;
  /** active / disabled */
  status: string;
  createdAt?: string;
  ticketCount: number;
}

export interface Contact {
  id: number;
  customerId: number;
  name: string;
  email?: string;
  phone?: string;
  title?: string;
  isPrimary?: boolean;
  createdAt?: string;
}

export interface CustomerPage {
  list: Customer[];
  total: number;
}

/** 客户 360 详情聚合视图 */
export interface CustomerDetail {
  customer: Customer;
  contacts: Contact[];
  tickets: TicketPage;
  recentTickets: Ticket[];
  avgCsat: number;
  csatCount: number;
  slaRate: number;
  resolvedCount: number;
  ticketTotal: number;
}

// ===== V2.3 智能服务升级 =====

/** M1 相似工单 */
export interface SimilarTicketItem {
  id: string;
  title: string;
  status: string;
  score: number;
  isMain: boolean;
}

export interface SimilarGroup {
  groupId: number | null;
  count: number;
  items: SimilarTicketItem[];
}

export interface SimilarityConfig {
  id?: number;
  tenantId?: number;
  threshold: number;
  enabled: boolean;
  windowDays: number;
}

export interface BatchReplyResult {
  sent: number;
  failed: Array<{ id: string; message: string }>;
}

/** M2 故障风暴 */
export interface StormItem {
  id: number;
  stormKey: string;
  category: string;
  count: number;
  windowMinutes: number;
  threshold: number;
  status: string;
  eventTicketId?: string | null;
  createdAt?: string;
}

/** M3 AI 评测 */
export interface EvalAdoptionItem {
  key: string;
  total: number;
  adopted: number;
  rejected: number;
  adoptionRate: number;
  rejectRate: number;
}

export interface EvalAdoption {
  total: number;
  byCategory: EvalAdoptionItem[];
  byEngineer: EvalAdoptionItem[];
}

export interface EvalCase {
  id: number;
  ticketId?: number;
  question: string;
  expectedPoints?: string;
  acceptCriteria?: string;
  rejectReason?: string;
  status: string;
  createdAt?: string;
}

export interface EvalRunResult {
  versionLabel: string;
  total: number;
  passed: number;
  passRate: number;
  prevPassRate?: number | null;
  delta?: number | null;
}

export interface EvalCluster {
  reason: string;
  count: number;
}

/** M4 L0 自助应答 */
export interface L0CategoryConfig {
  category: string;
  enabled: boolean;
  threshold: number;
}

export interface L0Config {
  enabled: boolean;
  categories: L0CategoryConfig[];
}

export interface L0ReportItem {
  category: string;
  total: number;
  resolved: number;
  reopened: number;
  transferred: number;
  resolveRate: number;
  miscloseRate: number;
  transferRate: number;
}

/** M5 情绪 */
export interface SentimentTrendPoint {
  date: string;
  count: number;
  negativeRatio: number;
  avgIntensity: number;
}

/** M6 摘要 */
export interface TicketSummaryResult {
  summary: string | null;
  edited?: boolean;
  insufficient: boolean;
  messageCount: number;
  message?: string;
  generatedAt?: string;
}

/** M7 客户健康分 */
export interface HealthComponents {
  freq: number;
  sla: number;
  csat: number;
  sentiment: number;
}

export interface HealthPoint {
  date: string;
  score: number;
}

export interface CustomerHealth {
  customerId: number;
  customerName: string;
  score: number;
  level: string;
  components: HealthComponents;
  trend: HealthPoint[];
}

export interface HealthConfig {
  id?: number;
  tenantId?: number;
  freqWeight: number;
  slaWeight: number;
  csatWeight: number;
  sentimentWeight: number;
}

/** GitHub Issue 同步配置 */
export interface GitHubSyncConfig {
  enabled: boolean;
  owner: string;
  repo: string;
  issueNumber: number | null;
  maxIssues: number;
  maxComments: number;
  hasToken: boolean;
  tokenHint: string | null;
  lastSyncAt: string | null;
  lastSyncResult: string | null;
}

export interface GitHubSyncResult {
  owner: string;
  repo: string;
  issueNumber: number | null;
  maxIssues: number;
  maxComments: number;
  message: string;
  issues: number;
  created: number;
  updated: number;
  skipped: number;
}
