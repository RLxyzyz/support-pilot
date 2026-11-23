import type {
  Ticket,
  TicketPage,
  KbDoc,
  KbDocPage,
  BoardData,
  UserInfo,
  LoginResponse,
  AgentStreamHandlers,
  TenantOption,
  ProductOption,
  OperationLog,
  OperationLogPage,
  Triage,
  DraftFeedbackStats,
  RelatedTicket,
  ReplyTemplate,
  Customer,
  Contact,
  CustomerPage,
  CustomerDetail,
  Team,
  AppNotification,
  NotificationPage,
  NotifyRule,
  MailTask,
  Attachment,
  SlaPolicy,
  SlaCalendar,
  SlaHoliday,
  SimilarGroup,
  SimilarityConfig,
  BatchReplyResult,
  StormItem,
  EvalAdoption,
  EvalCase,
  EvalRunResult,
  EvalCluster,
  L0Config,
  L0CategoryConfig,
  L0ReportItem,
  SentimentTrendPoint,
  TicketSummaryResult,
  CustomerHealth,
  HealthConfig,
  GitHubSyncConfig,
  GitHubSyncResult
} from '@/types';

// 统一返回体
interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

const BASE = import.meta.env.VITE_API_BASE || '/api';

const TOKEN_KEY = 'sp_token';
const USER_KEY = 'sp_user';

/** 读取本地 token（供请求头与 SSE query 使用） */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || '';
}

/** 后端滑动续签：把响应头里的新 token 覆盖到本地，登录态随操作自动延续 */
function absorbRefreshedToken(resp: Response): void {
  const next = resp.headers.get('X-Refreshed-Token');
  if (next) localStorage.setItem(TOKEN_KEY, next);
}

/** 登录成功后保存 token 与用户信息 */
export function setAuth(token: string, user: UserInfo): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** 退出 / 401 时清理登录态 */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/** 读取本地缓存的用户信息（用于侧边栏展示，避免每次刷新都请求 /me） */
export function getStoredUser(): UserInfo | null {
  try {
    const s = localStorage.getItem(USER_KEY);
    return s ? (JSON.parse(s) as UserInfo) : null;
  } catch {
    return null;
  }
}

/**
 * 是否管理员（大小写归一）。V2.0 迁移后 role 为大写 ADMIN；兼容历史小写值。
 */
export function isAdmin(): boolean {
  return (getStoredUser()?.role || '').toUpperCase() === 'ADMIN';
}

/**
 * 全局 toast 回调：由 ToastProvider 在挂载时注册，供 request() 等非 React 上下文弹出提示
 * （典型场景：HTTP 403 统一提示「无权限」）。
 */
let globalToast: ((msg: string) => void) | null = null;

export function registerToastHandler(fn: ((msg: string) => void) | null): void {
  globalToast = fn;
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }
  const resp = await fetch(BASE + url, {
    headers,
    ...options
  });
  absorbRefreshedToken(resp);
  // 登录失效：清理本地态并跳登录页
  if (resp.status === 401) {
    clearAuth();
    window.location.href = '/login';
    throw new Error('未登录或登录已过期');
  }
  // 无权限：统一 toast 提示（V2.0 EP2 RBAC）
  if (resp.status === 403) {
    globalToast?.('无权限访问该资源');
  }
  const json = (await resp.json()) as ApiResult<T>;
  if (!resp.ok || json.code !== 0) {
    throw new Error(json.message || `请求失败 (${resp.status})`);
  }
  return json.data;
}

/** multipart 上传：不设置 Content-Type，交给浏览器生成 boundary。 */
async function requestForm<T>(url: string, form: FormData): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const resp = await fetch(BASE + url, { method: 'POST', headers, body: form });
  absorbRefreshedToken(resp);
  if (resp.status === 401) {
    clearAuth();
    window.location.href = '/login';
    throw new Error('未登录或登录已过期');
  }
  const json = (await resp.json()) as ApiResult<T>;
  if (!resp.ok || json.code !== 0) {
    throw new Error(json.message || `请求失败 (${resp.status})`);
  }
  return json.data;
}

/** 鉴权下载：取 blob 后触发浏览器保存。 */
async function downloadBlob(url: string, fallbackName: string): Promise<void> {
  const token = getToken();
  const resp = await fetch(BASE + url, {
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  });
  absorbRefreshedToken(resp);
  if (resp.status === 401) {
    clearAuth();
    window.location.href = '/login';
    throw new Error('未登录或登录已过期');
  }
  if (!resp.ok) throw new Error(`下载失败 (${resp.status})`);
  const blob = await resp.blob();
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = fallbackName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(objectUrl);
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    }),
  register: (username: string, password: string, name: string) =>
    request<LoginResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, name })
    }),
  me: () => request<UserInfo>('/auth/me'),

  getTickets: (params: Record<string, string> = {}) => {
    const qs = new URLSearchParams(params).toString();
    return request<TicketPage>(`/tickets${qs ? '?' + qs : ''}`);
  },
  getTicket: (id: string) => request<Ticket>(`/tickets/${id}`),
  getQuickStats: () =>
    request<{ total: number; p1Open: number; processing: number; pending: number }>('/tickets/quick-stats'),
  // 基础数据下拉：客户（租户）、产品
  getTenants: () => request<TenantOption[]>('/tenants'),
  getProducts: (tenantId?: number) =>
    request<ProductOption[]>('/products' + (tenantId ? `?tenantId=${tenantId}` : '')),
  createTicket: (data: {
    title: string;
    body?: string;
    tenant?: string;
    tenantId?: number;
    customerId?: number;
    contactId?: number;
    productId?: number;
    priority?: string;
    category?: string;
  }) => request<Ticket>('/tickets', { method: 'POST', body: JSON.stringify(data) }),
  importTickets: (
    list: Array<{
      title: string;
      body?: string;
      priority?: string;
      category?: string;
      tenantId?: number;
      productId?: number;
      customerId?: number;
      contactId?: number;
      contactEmail?: string;
      customerName?: string;
    }>
  ) =>
    request<{ imported: number; skipped: number }>('/tickets/import', {
      method: 'POST',
      body: JSON.stringify(list)
    }),
  // 存量工单批量归户（仅未归户工单生效）
  backfillCustomer: (data: { ticketIds: number[]; customerId: number; contactId?: number }) =>
    request<{ updated: number; skipped: number }>('/tickets/backfill-customer', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  // 工单操作
  acceptTicket: (id: string) => request<Ticket>(`/tickets/${id}/accept`, { method: 'POST' }),
  /** 并发认领（V2.1 M3）：已被他人认领会返回 409，错误信息含当前处理人。 */
  claimTicket: (id: string) => request<Ticket>(`/tickets/${id}/claim`, { method: 'POST' }),
  assignTicket: (id: string, assigneeId: number) =>
    request<Ticket>(`/tickets/${id}/assign`, { method: 'POST', body: JSON.stringify({ assigneeId }) }),
  escalateTicket: (id: string) => request<Ticket>(`/tickets/${id}/escalate`, { method: 'POST' }),
  updateTicketStatus: (id: string, status: string) =>
    request<Ticket>(`/tickets/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),
  updateTicketPriority: (id: string, priority: string) =>
    request<Ticket>(`/tickets/${id}/priority`, { method: 'PUT', body: JSON.stringify({ priority }) }),
  updateTicketCategory: (id: string, category: string) =>
    request<Ticket>(`/tickets/${id}/category`, { method: 'PUT', body: JSON.stringify({ category }) }),
  addTicketMessage: (id: string, text: string, internal = false, attachmentIds: number[] = []) =>
    request<Ticket>(`/tickets/${id}/messages`, {
      method: 'POST',
      body: JSON.stringify({ text, internal, attachmentIds })
    }),
  // ===== V2.1 M4 工单附件 =====
  getTicketAttachments: (ticketId: string) => request<Attachment[]>(`/tickets/${ticketId}/attachments`),
  uploadAttachment: (ticketId: string, file: File, visibility = 'CUSTOMER') => {
    const form = new FormData();
    form.append('file', file);
    form.append('visibility', visibility);
    return requestForm<Attachment>(`/tickets/${ticketId}/attachments`, form);
  },
  deleteAttachment: (id: number) => request<void>(`/attachments/${id}`, { method: 'DELETE' }),
  updateAttachmentVisibility: (id: number, visibility: string) =>
    request<Attachment>(`/attachments/${id}/visibility?visibility=${encodeURIComponent(visibility)}`, {
      method: 'PUT'
    }),
  downloadAttachment: (id: number, fileName: string) =>
    downloadBlob(`/attachments/${id}/download`, fileName),
  previewAttachmentCleanup: () => request<Record<string, unknown>>('/attachments/cleanup/preview'),
  executeAttachmentCleanup: () =>
    request<Record<string, unknown>>('/attachments/cleanup/execute', { method: 'POST' }),
  getAttachmentStats: () => request<Record<string, unknown>>('/attachments/stats'),
  // ===== V2.1 M1 SLA 策略 / 日历 =====
  getSlaPolicies: () => request<SlaPolicy[]>('/sla/policies'),
  upsertSlaPolicy: (body: SlaPolicy) =>
    request<SlaPolicy>('/sla/policies', { method: 'PUT', body: JSON.stringify(body) }),
  getSlaCalendars: () => request<SlaCalendar[]>('/sla/calendars'),
  createSlaCalendar: (body: SlaCalendar) =>
    request<SlaCalendar>('/sla/calendars', { method: 'POST', body: JSON.stringify(body) }),
  updateSlaCalendar: (id: number, body: SlaCalendar) =>
    request<SlaCalendar>(`/sla/calendars/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteSlaCalendar: (id: number) => request<void>(`/sla/calendars/${id}`, { method: 'DELETE' }),
  getSlaHolidays: (calendarId: number) => request<SlaHoliday[]>(`/sla/calendars/${calendarId}/holidays`),
  addSlaHoliday: (calendarId: number, holidayDate: string, name?: string) =>
    request<SlaHoliday>(`/sla/calendars/${calendarId}/holidays`, {
      method: 'POST',
      body: JSON.stringify({ holidayDate, name })
    }),
  removeSlaHoliday: (calendarId: number, date: string) =>
    request<void>(`/sla/calendars/${calendarId}/holidays?date=${encodeURIComponent(date)}`, {
      method: 'DELETE'
    }),
  recalculateSla: () => request<{ updated: number; reason?: string }>('/sla/recalculate', { method: 'POST' }),
  backfillSla: () => request<{ updated: number }>('/sla/backfill', { method: 'POST' }),
  simulateSla: (tier: string, priority: string, start?: string) => {
    const params = new URLSearchParams({ tier, priority });
    if (start) params.set('start', start);
    return request<Record<string, unknown>>(`/sla/simulate?${params.toString()}`);
  },
  // 客户满意度（代录）
  submitCsat: (id: string, score: number, comment?: string) =>
    request<Ticket>(`/tickets/${id}/csat`, { method: 'POST', body: JSON.stringify({ score, comment }) }),
  // 工单关联（双向可见）
  getTicketRelations: (id: string) => request<RelatedTicket[]>(`/tickets/${id}/relations`),
  addTicketRelation: (id: string, relatedTicketId: number, relationType = 'related') =>
    request<RelatedTicket[]>(`/tickets/${id}/relations`, {
      method: 'POST',
      body: JSON.stringify({ relatedTicketId, relationType })
    }),
  deleteTicketRelation: (id: string, relationId: number) =>
    request<RelatedTicket[]>(`/tickets/${id}/relations/${relationId}`, { method: 'DELETE' }),

  // 快捷回复模板（按租户隔离）
  listReplyTemplates: () => request<ReplyTemplate[]>('/reply-templates'),
  createReplyTemplate: (data: { name: string; content: string; category?: string }) =>
    request<ReplyTemplate>('/reply-templates', { method: 'POST', body: JSON.stringify(data) }),
  updateReplyTemplate: (id: number, data: { name?: string; content?: string; category?: string }) =>
    request<ReplyTemplate>(`/reply-templates/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteReplyTemplate: (id: number) => request<void>(`/reply-templates/${id}`, { method: 'DELETE' }),

  getKbDocs: (productLine?: string, page = 1, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (productLine) params.set('productLine', productLine);
    return request<KbDocPage>(`/kb/documents?${params.toString()}`);
  },
  reindexKb: () => request<{ indexed: number; message: string }>('/kb/reindex', { method: 'POST' }),
  deleteKbDoc: (id: number) =>
    request<{ deleted: boolean; chunks?: number; message?: string }>(`/kb/documents/${id}`, { method: 'DELETE' }),
  reindexKbDoc: (id: number) =>
    request<{ indexed: number; message: string }>(`/kb/documents/${id}/reindex`, { method: 'POST' }),
  uploadKbDoc: (data: { name: string; content: string; productLine?: string }) =>
    request<{ docId: number; chunks: number; status: string; message?: string }>('/kb/documents', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  syncResolvedTickets: () =>
    request<{ total: number; ingested: number; skipped: number; failed: number; details?: string[] }>(
      '/kb/sync-resolved-tickets',
      { method: 'POST' }
    ),

  getMetrics: (days?: number, start?: string, end?: string) => {
    const params = new URLSearchParams();
    if (days) params.set('days', String(days));
    if (start) params.set('start', start);
    if (end) params.set('end', end);
    const qs = params.toString();
    return request<BoardData>(`/metrics/overview${qs ? '?' + qs : ''}`);
  },
  // AI 分诊建议
  getTriage: (ticketId: string | number) =>
    request<Triage>(`/agent/triage?ticketId=${encodeURIComponent(String(ticketId))}`),
  // 草稿反馈统计（采纳 / 驳回 / 驳回原因分布）
  getDraftFeedbackStats: () => request<DraftFeedbackStats>('/drafts/feedback-stats'),
  // 导出工单 CSV（后端生成，带鉴权头；不走 request() 因响应非 JSON）
  exportTickets: async (params: Record<string, string> = {}) => {
    const qs = new URLSearchParams(params).toString();
    const token = getToken();
    const resp = await fetch(BASE + '/tickets/export' + (qs ? '?' + qs : ''), {
      headers: token ? { Authorization: 'Bearer ' + token } : {}
    });
    absorbRefreshedToken(resp);
    if (resp.status === 401) {
      clearAuth();
      window.location.href = '/login';
      throw new Error('未登录或登录已过期');
    }
    if (!resp.ok) throw new Error(`导出失败 (${resp.status})`);
    const blob = await resp.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const cd = resp.headers.get('Content-Disposition') || '';
    const m = cd.match(/filename=(.+)/);
    a.download = m ? m[1].replace(/"/g, '') : `tickets-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  },

  approveDraft: (id: string, content: string) =>
    request<{ adopted: boolean; cost: string }>(`/drafts/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ content })
    }),
  rejectDraft: (id: string, reason?: string, comment?: string) =>
    request<{ rejected: boolean }>(`/drafts/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason || '', comment: comment || '' })
    }),

  // ===== V2.0 EP1 客户域 =====
  getCustomers: (params: Record<string, string | number> = {}) => {
    const qs = new URLSearchParams(
      Object.entries(params).reduce<Record<string, string>>((acc, [k, v]) => {
        if (v !== undefined && v !== null && v !== '') acc[k] = String(v);
        return acc;
      }, {})
    ).toString();
    return request<CustomerPage>(`/customers${qs ? '?' + qs : ''}`);
  },
  getCustomer: (id: number, page = 1, size = 10) =>
    request<CustomerDetail>(`/customers/${id}?page=${page}&size=${size}`),
  createCustomer: (data: {
    name: string;
    code: string;
    slaTier?: string;
    contractNo?: string;
    contractStart?: string;
    contractEnd?: string;
    status?: string;
  }) => request<Customer>('/customers', { method: 'POST', body: JSON.stringify(data) }),
  updateCustomer: (
    id: number,
    data: {
      name?: string;
      code?: string;
      slaTier?: string;
      contractNo?: string;
      contractStart?: string;
      contractEnd?: string;
      status?: string;
    }
  ) => request<Customer>(`/customers/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  disableCustomer: (id: number) => request<Customer>(`/customers/${id}/disable`, { method: 'POST' }),
  deleteCustomer: (id: number) => request<void>(`/customers/${id}`, { method: 'DELETE' }),

  // 客户联系人（嵌套资源 /api/customers/{customerId}/contacts）
  getContacts: (customerId: number) => request<Contact[]>(`/customers/${customerId}/contacts`),
  createContact: (
    customerId: number,
    data: { name: string; email?: string; phone?: string; title?: string; isPrimary?: boolean }
  ) => request<Contact>(`/customers/${customerId}/contacts`, { method: 'POST', body: JSON.stringify(data) }),
  updateContact: (
    customerId: number,
    id: number,
    data: { name?: string; email?: string; phone?: string; title?: string; isPrimary?: boolean }
  ) => request<Contact>(`/customers/${customerId}/contacts/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  setPrimaryContact: (customerId: number, id: number) =>
    request<Contact>(`/customers/${customerId}/contacts/${id}/primary`, { method: 'POST' }),
  deleteContact: (customerId: number, id: number) =>
    request<void>(`/customers/${customerId}/contacts/${id}`, { method: 'DELETE' }),

  // 用户管理（ADMIN）
  listUsers: () => request<UserInfo[]>('/users'),
  createUser: (data: {
    username: string;
    password: string;
    name: string;
    email?: string;
    role?: string;
    tenantId?: number;
    teamId?: number;
    dataScope?: string;
  }) => request<UserInfo>('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (
    id: number,
    data: {
      username?: string;
      password?: string;
      name?: string;
      email?: string;
      role?: string;
      tenantId?: number;
      teamId?: number;
      dataScope?: string;
    }
  ) => request<UserInfo>(`/users/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteUser: (id: number) => request<void>(`/users/${id}`, { method: 'DELETE' }),

  // 个人设置（所有登录用户）
  getMe: () => request<UserInfo>('/users/me'),
  updateMe: (data: { name?: string; email?: string; password?: string }) =>
    request<UserInfo>('/users/me', { method: 'PUT', body: JSON.stringify(data) }),

  // 工作组（V2.0 EP2）
  getTeams: () => request<Team[]>('/teams'),
  createTeam: (data: { name: string }) =>
    request<Team>('/teams', { method: 'POST', body: JSON.stringify(data) }),
  updateTeam: (id: number, data: { name: string }) =>
    request<Team>(`/teams/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  disableTeam: (id: number) => request<Team>(`/teams/${id}/disable`, { method: 'POST' }),

  // 站内信（V2.0 EP3）
  getUnreadCount: () => request<{ count: number }>('/notifications/unread-count'),
  getNotifications: (type?: string, page = 1, size = 20) =>
    request<NotificationPage>(
      `/notifications?page=${page}&size=${size}` + (type ? `&type=${encodeURIComponent(type)}` : '')
    ),
  markNotificationRead: (id: number) =>
    request<void>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllNotificationsRead: () =>
    request<{ updated: number }>('/notifications/read-all', { method: 'POST' }),

  // 通知规则（ADMIN）
  getNotifyRules: () => request<NotifyRule[]>('/notify-rules'),
  updateNotifyRule: (id: number, data: Partial<NotifyRule>) =>
    request<NotifyRule>(`/notify-rules/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  // 邮件任务（ADMIN）
  listMailTasks: (status?: string) =>
    request<MailTask[]>(
      '/mail-tasks' + (status ? `?status=${encodeURIComponent(status)}` : '')
    ),
  resendMailTask: (id: number) =>
    request<MailTask>(`/mail-tasks/${id}/resend`, { method: 'POST' }),

  // 操作审计日志（仅 admin）
  getOperationLogs: (params: Record<string, string> = {}) => {
    const qs = new URLSearchParams(params).toString();
    return request<OperationLogPage>(`/operation-logs${qs ? '?' + qs : ''}`);
  },
  getOperationLog: (id: number) => request<OperationLog>(`/operation-logs/${id}`),

  // ===== V2.3 智能服务 =====
  // M1 相似工单
  getTicketSimilar: (id: string) => request<SimilarGroup>(`/tickets/${id}/similar`),
  batchReplySimilar: (id: string, content: string, ticketIds?: number[]) =>
    request<BatchReplyResult>(`/tickets/${id}/similar/batch-reply`, {
      method: 'POST',
      body: JSON.stringify({ content, ticketIds })
    }),
  mergeSimilarTicket: (id: string, targetId: number) =>
    request<Record<string, unknown>>(`/tickets/${id}/similar/merge`, {
      method: 'POST',
      body: JSON.stringify({ targetId })
    }),
  getSimilarityConfig: () => request<SimilarityConfig>('/similarity/config'),
  updateSimilarityConfig: (data: Partial<SimilarityConfig>) =>
    request<SimilarityConfig>('/similarity/config', { method: 'PUT', body: JSON.stringify(data) }),

  // GitHub Issue 同步配置
  getGitHubSyncConfig: () => request<GitHubSyncConfig>('/github-sync/config'),
  updateGitHubSyncConfig: (data: {
    enabled: boolean;
    owner: string;
    repo: string;
    issueNumber: number | null;
    maxIssues: number;
    maxComments: number;
    token?: string;
  }) => request<GitHubSyncConfig>('/github-sync/config', { method: 'PUT', body: JSON.stringify(data) }),
  runGitHubSync: () => request<GitHubSyncResult>('/github-sync/run', { method: 'POST' }),

  // M2 故障风暴
  getActiveStorms: () => request<StormItem[]>('/storms/active'),
  upgradeStorm: (id: number) =>
    request<{ eventTicketId: string; related: number }>(`/storms/${id}/upgrade`, { method: 'POST' }),
  recoverStorm: (id: number) =>
    request<Record<string, unknown>>(`/storms/${id}/recover`, { method: 'POST' }),
  dismissStorm: (id: number) =>
    request<Record<string, unknown>>(`/storms/${id}/dismiss`, { method: 'POST' }),

  // M3 AI 评测
  getEvalAdoption: () => request<EvalAdoption>('/eval/adoption'),
  sedimentEvalCase: (data: {
    ticketId?: number;
    draftId?: number;
    question?: string;
    expectedPoints?: string;
  }) => request<EvalCase>('/eval/cases/sediment', { method: 'POST', body: JSON.stringify(data) }),
  getEvalCases: () => request<EvalCase[]>('/eval/cases'),
  runEval: (versionLabel?: string) =>
    request<EvalRunResult>('/eval/runs', { method: 'POST', body: JSON.stringify({ versionLabel }) }),
  getEvalClusters: () => request<EvalCluster[]>('/eval/clusters'),

  // M4 L0 自助应答
  getL0Config: () => request<L0Config>('/l0/config'),
  updateL0Config: (confirm: boolean, categories: L0CategoryConfig[]) =>
    request<L0Config>('/l0/config', { method: 'PUT', body: JSON.stringify({ confirm, categories }) }),
  confirmL0: (token: string) =>
    request<Record<string, unknown>>(`/l0/confirm?token=${encodeURIComponent(token)}`, { method: 'POST' }),
  reopenL0: (ticketId: string) =>
    request<Record<string, unknown>>(`/l0/reopen/${ticketId}`, { method: 'POST' }),
  transferL0: (ticketId: string) =>
    request<Record<string, unknown>>(`/l0/transfer/${ticketId}`, { method: 'POST' }),
  getL0Report: () => request<L0ReportItem[]>('/l0/report'),

  // M5 情绪
  upgradeSentiment: (ticketId: string) =>
    request<{ upgraded: boolean; priority: string }>(`/tickets/${ticketId}/sentiment/upgrade`, {
      method: 'POST'
    }),
  getCustomerSentimentTrend: (customerId: number) =>
    request<SentimentTrendPoint[]>(`/customers/${customerId}/sentiment/trend`),

  // M6 摘要
  getTicketSummary: (id: string) => request<TicketSummaryResult>(`/tickets/${id}/summary`),
  updateTicketSummary: (id: string, content: string) =>
    request<TicketSummaryResult>(`/tickets/${id}/summary`, {
      method: 'PUT',
      body: JSON.stringify({ content })
    }),

  // M7 客户健康分
  getCustomerHealth: (customerId: number) => request<CustomerHealth>(`/customers/${customerId}/health`),
  getHealthConfig: () => request<HealthConfig>('/health/config'),
  updateHealthConfig: (data: Partial<HealthConfig>) =>
    request<HealthConfig>('/health/config', { method: 'PUT', body: JSON.stringify(data) })
};

/**
 * 订阅 Agent 处理的 SSE 流。浏览器 EventSource 仅支持 GET 且无法设置自定义 header，
 * 故后端 /api/agent/runs 支持 query 参数 token 鉴权，这里把本地 token 拼到 URL 上。
 */
export function streamAgent(ticketId: string, handlers: AgentStreamHandlers): () => void {
  const token = getToken();
  const es = new EventSource(
    `${BASE}/agent/runs?ticketId=${encodeURIComponent(ticketId)}&token=${encodeURIComponent(token)}`
  );

  const listen = (event: string, cb: (data: any) => void) => {
    es.addEventListener(event, (e: MessageEvent) => {
      try {
        cb(JSON.parse(e.data));
      } catch {
        cb(e.data);
      }
    });
  };

  listen('thinking', (d) => handlers.onThinking?.(d.label));
  listen('step', (d) => handlers.onStep?.(d));
  listen('draft', (d) => handlers.onDraft?.(d));
  listen('cites', (d) => handlers.onCites?.(d.items));
  listen('done', () => {
    handlers.onDone?.();
    es.close();
  });
  es.onerror = (e) => {
    // 流结束时浏览器也会触发 error，这里只在未完成时上报
    handlers.onError?.(e);
  };

  return () => es.close();
}
