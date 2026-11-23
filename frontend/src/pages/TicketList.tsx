import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { Ticket, Customer, Contact, ProductOption, UserInfo, StormItem, SimilarGroup } from '@/types';

/** 本地日期串 → ISO-8601（endOfDay 取当天 23:59:59），与后端 Instant.parse 对齐 */
function toISO(d: string, endOfDay: boolean): string {
  return new Date(d + (endOfDay ? 'T23:59:59' : 'T00:00:00')).toISOString();
}

const STATUS_CHIPS = ['全部', '待分诊', '处理中', '待客户', '已解决'];
const PRIORITIES = ['全部', 'P1', 'P2', 'P3', 'P4'];
const PAGE_SIZES = [10, 20, 50];

const PRIORITY_OPTIONS = [
  { v: 'P1', t: '紧急', d: '生产不可用，需立即响应' },
  { v: 'P2', t: '高', d: '核心功能受损，影响业务' },
  { v: 'P3', t: '中', d: '一般问题，可正常排期' },
  { v: 'P4', t: '低', d: '咨询 / 建议类问题' }
];

const CATEGORY_OPTIONS = [
  '未分类',
  '技术故障',
  '账号与登录',
  '性能与超时',
  '数据与报表',
  '支付与计费',
  '接口与集成',
  '部署与配置',
  '网络与可用性',
  '产品使用咨询',
  '需求与建议',
  '其他'
];

const priCls = (v: string) => (v === 'P1' ? 'p1' : v === 'P2' ? 'p2' : v === 'P3' ? 'p3' : 'p4');

export default function TicketList() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState('全部');
  const [priority, setPriority] = useState('全部');
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(true);
  const [p1Open, setP1Open] = useState(0);

  // 扩展筛选：处理人 / 产品 / 时间范围 / 仅看超时
  const [assigneeId, setAssigneeId] = useState<number | null>(null);
  const [productId, setProductId] = useState<number | null>(null);
  const [dateStart, setDateStart] = useState('');
  const [dateEnd, setDateEnd] = useState('');
  const [overdue, setOverdue] = useState(false);

  // 存量工单归户（PRD EX-03）：仅看未归户 + 勾选后批量归户
  const [unassigned, setUnassigned] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);
  const [backfillOpen, setBackfillOpen] = useState(false);
  const [backfillCustomerId, setBackfillCustomerId] = useState<number | null>(null);
  const [backfillContactId, setBackfillContactId] = useState<number | null>(null);
  const [backfillContacts, setBackfillContacts] = useState<Contact[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [filterProducts, setFilterProducts] = useState<ProductOption[]>([]);
  const [exporting, setExporting] = useState(false);

  // 新建工单弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [createTitle, setCreateTitle] = useState('');
  const [createBody, setCreateBody] = useState('');
  const [createPri, setCreatePri] = useState('P3');
  const [createCat, setCreateCat] = useState('未分类');
  const [createCustomerId, setCreateCustomerId] = useState<number | null>(null);
  const [createTenantId, setCreateTenantId] = useState<number | null>(null); // 派生自所选客户，用于产品过滤
  const [createContactId, setCreateContactId] = useState<number | null>(null);
  const [createProductId, setCreateProductId] = useState<number | null>(null);
  /** V2.1 M4：创建工单时附带的文件，创建成功后批量上传 */
  const [createFiles, setCreateFiles] = useState<File[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [products, setProducts] = useState<ProductOption[]>([]);

  // V2.3 智能服务：故障风暴横幅 + 相似工单抽屉
  const [storms, setStorms] = useState<StormItem[]>([]);
  const [similarOpen, setSimilarOpen] = useState(false);
  const [similarData, setSimilarData] = useState<SimilarGroup | null>(null);
  const [similarLoading, setSimilarLoading] = useState(false);
  const [similarReply, setSimilarReply] = useState('');
  const [similarSending, setSimilarSending] = useState(false);

  /** 列表与导出共用的筛选参数（不含分页）。 */
  const buildFilterParams = (): Record<string, string> => {
    const params: Record<string, string> = {};
    if (status !== '全部') params.status = status;
    if (priority !== '全部') params.priority = priority;
    if (keyword.trim()) params.keyword = keyword.trim();
    if (assigneeId) params.assigneeId = String(assigneeId);
    if (productId) params.productId = String(productId);
    if (dateStart) params.start = toISO(dateStart, false);
    if (dateEnd) params.end = toISO(dateEnd, true);
    if (overdue) params.overdue = 'true';
    if (unassigned) params.unassigned = 'true';
    return params;
  };

  const load = async () => {
    setLoading(true);
    setSelected([]);
    try {
      const params = buildFilterParams();
      params.page = String(page);
      params.size = String(size);
      const data = await api.getTickets(params);
      setTickets(data.list);
      setTotal(data.total);
    } catch (e) {
      showToast('加载工单失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const doExport = async () => {
    setExporting(true);
    try {
      await api.exportTickets(buildFilterParams());
      showToast('已导出工单 CSV');
    } catch (e) {
      showToast('导出失败：' + (e as Error).message);
    } finally {
      setExporting(false);
    }
  };

  const loadStats = () => {
    api
      .getQuickStats()
      .then((s) => setP1Open(s.p1Open))
      .catch(() => {});
  };

  const loadStorms = () => {
    api.getActiveStorms().then(setStorms).catch(() => {});
  };

  const openSimilar = async (e: React.MouseEvent, t: Ticket) => {
    e.stopPropagation();
    setSimilarOpen(true);
    setSimilarLoading(true);
    setSimilarData(null);
    setSimilarReply('');
    try {
      setSimilarData(await api.getTicketSimilar(t.id.replace('#', '')));
    } catch (err) {
      showToast('加载相似工单失败：' + (err as Error).message);
    } finally {
      setSimilarLoading(false);
    }
  };

  const sendSimilarReply = async () => {
    if (!similarData || !similarReply.trim()) return;
    setSimilarSending(true);
    try {
      const r = await api.batchReplySimilar(
        String(similarData.groupId),
        similarReply.trim()
      );
      showToast(
        r.failed.length === 0
          ? `批量回复成功 ${r.sent} 条`
          : `成功 ${r.sent} 条，失败 ${r.failed.length} 条`
      );
      setSimilarReply('');
      load();
    } catch (err) {
      showToast('批量回复失败：' + (err as Error).message);
    } finally {
      setSimilarSending(false);
    }
  };

  // 挂载时读取 URL keyword（来自顶栏全局搜索），设置后会触发下方 load effect
  useEffect(() => {
    const kw = new URLSearchParams(window.location.search).get('keyword');
    if (kw) {
      setKeywordInput(kw);
      setKeyword(kw);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 挂载时加载处理人 / 产品下拉数据（筛选条用）
  useEffect(() => {
    api.listUsers().then(setUsers).catch(() => {});
    api.getProducts().then(setFilterProducts).catch(() => {});
    loadStorms();
    const timer = setInterval(loadStorms, 60000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    load();
    loadStats();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, priority, keyword, page, size, assigneeId, productId, dateStart, dateEnd, overdue, unassigned]);

  // 新建弹窗里，客户变化时按客户过滤产品
  useEffect(() => {
    if (!createOpen || !createTenantId) {
      setProducts([]);
      return;
    }
    api
      .getProducts(createTenantId)
      .then(setProducts)
      .catch(() => setProducts([]));
  }, [createOpen, createTenantId]);

  const totalPages = Math.max(Math.ceil(total / size), 1);

  const pageNumbers = (): (number | '...')[] => {
    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }
    const pages: (number | '...')[] = [1];
    const start = Math.max(2, page - 1);
    const end = Math.min(totalPages - 1, page + 1);
    if (start > 2) pages.push('...');
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < totalPages - 1) pages.push('...');
    pages.push(totalPages);
    return pages;
  };

  const openTicket = (t: Ticket) => {
    const id = t.id.replace('#', '');
    navigate(`/tickets/${id}`);
  };

  const doImport = async () => {
    try {
      // V2.0 EP1：导入的工单也必须挂客户（后端强校验），默认归入第一个客户
      let list = customers;
      if (list.length === 0) {
        const data = await api.getCustomers({ page: 1, size: 200 });
        list = data.list;
        setCustomers(list);
      }
      if (list.length === 0) {
        showToast('请先创建客户后再导入工单');
        return;
      }
      const customerId = list[0].id;
      const samples = [
        { title: '导入示例：登录后页面白屏', body: '客户反馈登录后主页面空白，浏览器控制台有报错。', priority: 'P2', category: '前端', customerId },
        { title: '导入示例：大数据量导出报表超时', body: '导出超过 10 万行时接口超时。', priority: 'P3', category: '报表', customerId }
      ];
      const r = await api.importTickets(samples);
      showToast(`导入完成：成功 ${r.imported} 条，跳过 ${r.skipped} 条`);
      setPage(1);
      load();
      loadStats();
    } catch (e) {
      showToast('导入失败：' + (e as Error).message);
    }
  };

  const doCreate = async () => {
    setCreateTitle('');
    setCreateBody('');
    setCreatePri('P3');
    setCreateCat('未分类');
    setCreateCustomerId(null);
    setCreateTenantId(null);
    setCreateContactId(null);
    setCreateProductId(null);
    setContacts([]);
    setCreateOpen(true);
    if (customers.length === 0) {
      try {
        const data = await api.getCustomers({ page: 1, size: 200 });
        setCustomers(data.list);
      } catch (e) {
        showToast('加载客户列表失败：' + (e as Error).message);
      }
    }
  };

  const confirmCreate = async () => {
    if (!createTitle.trim()) return;
    if (!createCustomerId) {
      showToast('请选择客户');
      return;
    }
    let ticketId = '';
    try {
      const t = await api.createTicket({
        title: createTitle.trim(),
        body: createBody.trim() || undefined,
        priority: createPri,
        category: createCat,
        customerId: createCustomerId,
        contactId: createContactId ?? undefined,
        tenantId: createTenantId ?? undefined,
        productId: createProductId ?? undefined
      });
      ticketId = t.id;
      // V2.1 M4：创建成功后批量上传附件
      const numericId = ticketId.replace('#', '');
      for (const f of createFiles) {
        await api.uploadAttachment(numericId, f, 'CUSTOMER');
      }
      setCreateOpen(false);
      setCreateFiles([]);
      showToast(createFiles.length > 0 ? `工单已创建，${createFiles.length} 个附件已上传` : '工单已创建');
      setPage(1);
      load();
      loadStats();
    } catch (e) {
      showToast('创建失败：' + (e as Error).message);
    }
  };

  const onStatusChange = (s: string) => {
    setStatus(s);
    setPage(1);
  };
  const onPriorityChange = (p: string) => {
    setPriority(p);
    setPage(1);
  };
  const onSearch = () => {
    setKeyword(keywordInput.trim());
    setPage(1);
  };
  const onSizeChange = (s: number) => {
    setSize(s);
    setPage(1);
  };
  const onAssigneeChange = (v: string) => {
    setAssigneeId(v ? Number(v) : null);
    setPage(1);
  };
  const onProductChange = (v: string) => {
    setProductId(v ? Number(v) : null);
    setPage(1);
  };
  const onDateStartChange = (v: string) => {
    setDateStart(v);
    setPage(1);
  };
  const onDateEndChange = (v: string) => {
    setDateEnd(v);
    setPage(1);
  };
  const onOverdueChange = (v: boolean) => {
    setOverdue(v);
    setPage(1);
  };

  const toggleSelected = (id: number) => {
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };
  const ticketNum = (t: Ticket) => Number(t.id.replace('#', ''));
  const toggleAll = () => {
    setSelected((prev) => (prev.length === tickets.length ? [] : tickets.map(ticketNum)));
  };

  const openBackfill = async (ids: number[]) => {
    if (ids.length === 0) return;
    setSelected(ids);
    setBackfillCustomerId(null);
    setBackfillContactId(null);
    setBackfillContacts([]);
    setBackfillOpen(true);
    if (customers.length === 0) {
      try {
        const data = await api.getCustomers({ page: 1, size: 200 });
        setCustomers(data.list);
      } catch (e) {
        showToast('加载客户列表失败：' + (e as Error).message);
      }
    }
  };

  const doBackfill = async () => {
    if (!backfillCustomerId) {
      showToast('请选择目标客户');
      return;
    }
    try {
      const r = await api.backfillCustomer({
        ticketIds: selected,
        customerId: backfillCustomerId,
        contactId: backfillContactId ?? undefined
      });
      setBackfillOpen(false);
      showToast(`归户完成：成功 ${r.updated} 条，跳过 ${r.skipped} 条`);
      load();
    } catch (e) {
      showToast('归户失败：' + (e as Error).message);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">工单</div>
          <div className="page-sub">
            共 {total.toLocaleString()} 条 · 其中 {p1Open} 条 P1 待处理 · 数据源 spring-projects/spring-boot
          </div>
        </div>
        <div className="spacer"></div>
        <button className="btn" onClick={doImport}>
          批量导入
        </button>
        <button className="btn primary" onClick={doCreate}>
          + 新建工单
        </button>
      </div>

      {storms.length > 0 && (
        <div
          style={{
            margin: '0 0 12px',
            padding: '10px 14px',
            borderRadius: 10,
            border: '1px solid #f0c36d',
            background: '#fffaf0',
            display: 'flex',
            gap: 10,
            alignItems: 'center',
            flexWrap: 'wrap'
          }}
        >
          <b style={{ color: '#b45309' }}>故障风暴</b>
          {storms.map((s) => (
            <span
              key={s.id}
              className="pill"
              style={{ background: '#fef3c7', color: '#92400e' }}
            >
              {s.category} · {s.count} 单 / {s.windowMinutes} 分钟
            </span>
          ))}
          <span className="spacer"></span>
          <button className="btn ghost sm" onClick={() => navigate('/settings/smart')}>
            去处理
          </button>
        </div>
      )}

      <div className="filterbar">
        <div className="chips">
          {STATUS_CHIPS.map((s) => (
            <div
              key={s}
              className={'chip' + (status === s ? ' active' : '')}
              onClick={() => onStatusChange(s)}
            >
              {s}
            </div>
          ))}
        </div>
        <div className="spacer"></div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <select
            value={priority}
            onChange={(e) => onPriorityChange(e.target.value)}
            className="select"
            style={{ width: 'auto', height: 30, padding: '0 8px' }}
          >
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                优先级：{p}
              </option>
            ))}
          </select>
          <input
            className="input"
            placeholder="搜索标题/正文…"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && onSearch()}
            style={{ width: 180, height: 30 }}
          />
          <button className="btn sm" onClick={onSearch}>
            搜索
          </button>
        </div>
      </div>

      <div className="filterbar" style={{ paddingTop: 0, borderTop: 'none' }}>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            value={assigneeId ?? ''}
            onChange={(e) => onAssigneeChange(e.target.value)}
            className="select"
            style={{ width: 'auto', height: 30, padding: '0 8px' }}
          >
            <option value="">处理人：全部</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name || u.username}
              </option>
            ))}
          </select>
          <select
            value={productId ?? ''}
            onChange={(e) => onProductChange(e.target.value)}
            className="select"
            style={{ width: 'auto', height: 30, padding: '0 8px' }}
          >
            <option value="">产品：全部</option>
            {filterProducts.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <span style={{ color: 'var(--muted-2)', fontSize: 12 }}>创建</span>
          <input
            type="date"
            value={dateStart}
            onChange={(e) => onDateStartChange(e.target.value)}
            className="input"
            style={{ height: 30, width: 140 }}
          />
          <span style={{ color: 'var(--muted-2)', fontSize: 12 }}>至</span>
          <input
            type="date"
            value={dateEnd}
            onChange={(e) => onDateEndChange(e.target.value)}
            className="input"
            style={{ height: 30, width: 140 }}
          />
          <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
            <input
              type="checkbox"
              checked={overdue}
              onChange={(e) => onOverdueChange(e.target.checked)}
              style={{ accentColor: '#2563eb' }}
            />
            仅看超时
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
            <input
              type="checkbox"
              checked={unassigned}
              onChange={(e) => {
                setUnassigned(e.target.checked);
                setPage(1);
              }}
              style={{ accentColor: '#2563eb' }}
            />
            仅看未归户
          </label>
        </div>
        <div className="spacer"></div>
        {unassigned && (
          <button className="btn sm" disabled={selected.length === 0} onClick={() => openBackfill(selected)}>
            批量归户（{selected.length}）
          </button>
        )}
        <button className="btn sm" onClick={doExport} disabled={exporting}>
          {exporting ? '导出中…' : '导出 CSV'}
        </button>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              {unassigned && (
                <th style={{ width: 34 }}>
                  <input
                    type="checkbox"
                    checked={tickets.length > 0 && selected.length === tickets.length}
                    onChange={toggleAll}
                  />
                </th>
              )}
              <th style={{ width: 78 }}>编号</th>
              <th>标题</th>
              <th style={{ width: 118 }}>客户</th>
              <th style={{ width: 110 }}>产品</th>
              <th style={{ width: 70 }}>优先级</th>
              <th style={{ width: 88 }}>状态</th>
              <th style={{ width: 130 }}>SLA</th>
              <th style={{ width: 88 }}>更新时间</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={unassigned ? 9 : 8}>
                  <div className="empty">加载中…</div>
                </td>
              </tr>
            ) : tickets.length === 0 ? (
              <tr>
                <td colSpan={unassigned ? 9 : 8}>
                  <div className="empty">暂无工单</div>
                </td>
              </tr>
            ) : (
              tickets.map((t) => (
                <tr key={t.id} onClick={() => openTicket(t)}>
                  {unassigned && (
                    <td onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={selected.includes(ticketNum(t))}
                        onChange={() => toggleSelected(ticketNum(t))}
                      />
                    </td>
                  )}
                  <td>
                    <span className="t-id">{t.id}</span>
                  </td>
                  <td>
                    <span className="t-title">{t.title}</span>
                    <span className="t-sub">{t.category}</span>
                    {(t.similarCount ?? 0) > 0 && (
                      <span
                        className="pill soft"
                        style={{ marginLeft: 6, cursor: 'pointer' }}
                        title="查看相似工单组"
                        onClick={(e) => openSimilar(e, t)}
                      >
                        相似 {t.similarCount}
                      </span>
                    )}
                    {t.l0State === 'AUTO_REPLIED' && (
                      <span className="pill soft" style={{ marginLeft: 6 }} title="L0 已自动应答">
                        AI 自助
                      </span>
                    )}
                  </td>
                  <td style={{ color: 'var(--ink-2)' }}>
                    {t.customerName || <span style={{ color: 'var(--warn)' }}>未归户</span>}
                  </td>
                  <td style={{ color: 'var(--ink-2)', fontSize: 12 }}>
                    {t.product || '—'}
                  </td>
                  <td>
                    <span className={'pill ' + t.priorityClass}>{t.priority}</span>
                  </td>
                  <td>
                    <span className={'pill ' + t.statusClass}>{t.status}</span>
                  </td>
                  <td>
                    <div className="sla">
                      <div className="sla-txt" style={{ color: t.slaColor }}>
                        {t.sla}
                      </div>
                      <div className="sla-bar">
                        <i style={{ width: t.slaPct + '%', background: t.slaColor }}></i>
                      </div>
                    </div>
                  </td>
                  <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>{t.updateTime}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="list-foot">
        <div>
          共 <b style={{ color: 'var(--ink)' }}>{total.toLocaleString()}</b> 条 · 第{' '}
          <b style={{ color: 'var(--ink)' }}>{page}</b> / {totalPages} 页
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          每页
          <select
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
            className="select"
            style={{ width: 'auto', height: 26, padding: '0 6px' }}
          >
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          条
        </label>
        <div className="pager">
          <button className="pg" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
            ‹
          </button>
          {pageNumbers().map((p, idx) =>
            p === '...' ? (
              <span key={`e-${idx}`} style={{ padding: '0 4px', color: 'var(--muted-2)', alignSelf: 'center' }}>
                …
              </span>
            ) : (
              <button
                key={p}
                className={'pg' + (p === page ? ' active' : '')}
                onClick={() => setPage(p)}
              >
                {p}
              </button>
            )
          )}
          <button
            className="pg"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            ›
          </button>
        </div>
      </div>

      {/* 新建工单弹窗 */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="新建工单"
        subtitle="手工创建一条客户工单"
        icon="+"
        footer={
          <>
            <span className="note">优先级默认 P3，可随时在详情页调整</span>
            <button className="btn ghost sm" onClick={() => setCreateOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmCreate} disabled={!createTitle.trim() || !createCustomerId}>
              创建工单
            </button>
          </>
        }
      >
        <div className="field">
          <label>
            工单标题 <span className="req">*</span>
          </label>
          <input
            className="input"
            autoFocus
            value={createTitle}
            onChange={(e) => setCreateTitle(e.target.value)}
            placeholder="简要描述客户遇到的问题"
          />
        </div>
        <div className="field">
          <label>
            问题描述
            <span className="desc">详细现象、发生时间、影响范围与已尝试的排查步骤</span>
          </label>
          <textarea
            className="textarea"
            rows={5}
            value={createBody}
            onChange={(e) => setCreateBody(e.target.value)}
            placeholder="例如：今天 10:20 起部分用户登录后页面空白，控制台报 500，影响约 30 名用户；已确认非网络问题。"
          />
        </div>
        <div className="field">
          <label>
            客户 <span className="req">*</span>
          </label>
          <select
            className="select"
            value={createCustomerId ?? ''}
            onChange={(e) => {
              const cid = e.target.value ? Number(e.target.value) : null;
              setCreateCustomerId(cid);
              const cust = cid ? customers.find((c) => c.id === cid) : undefined;
              setCreateTenantId(cust?.tenantId ?? null);
              setCreateContactId(null);
              setCreateProductId(null);
              if (cid) {
                api.getContacts(cid).then(setContacts).catch(() => setContacts([]));
              } else {
                setContacts([]);
              }
            }}
          >
            <option value="">请选择客户</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}（{c.code} · {c.slaTier}）
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>联系人</label>
          <select
            className="select"
            value={createContactId ?? ''}
            onChange={(e) => setCreateContactId(e.target.value ? Number(e.target.value) : null)}
            disabled={!createCustomerId}
          >
            <option value="">不指定</option>
            {contacts.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
                {c.isPrimary ? '（主联系人）' : ''}
              </option>
            ))}
          </select>
          {!createCustomerId ? <div className="desc">请先选择客户后再选择联系人</div> : null}
        </div>
        <div className="field">
          <label>产品</label>
          <select
            className="select"
            value={createProductId ?? ''}
            onChange={(e) => setCreateProductId(e.target.value ? Number(e.target.value) : null)}
            disabled={!createTenantId || products.length === 0}
          >
            <option value="">不指定</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          {createTenantId && products.length === 0 ? (
            <div className="desc">该客户下暂无产品，可不指定</div>
          ) : null}
        </div>
        <div className="field">
          <label>优先级</label>
          <div className="pri-grid">
            {PRIORITY_OPTIONS.map((o) => (
              <div
                key={o.v}
                className={'pri-opt' + (createPri === o.v ? ' sel' : '')}
                onClick={() => setCreatePri(o.v)}
              >
                <div className="h">
                  <span className={'pill ' + priCls(o.v)}>{o.v}</span>
                  {o.t}
                </div>
                <div className="d">{o.d}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="field">
          <label>分类</label>
          <select className="select" value={createCat} onChange={(e) => setCreateCat(e.target.value)}>
            {CATEGORY_OPTIONS.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>
            报错截图 / 附件
            <span className="desc">创建后自动上传，可多选</span>
          </label>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <label className="btn ghost sm" style={{ cursor: 'pointer' }}>
              选择文件
              <input
                type="file"
                multiple
                style={{ display: 'none' }}
                accept=".png,.jpg,.jpeg,.gif,.pdf,.txt,.log,.csv,.zip,.json"
                onChange={(e) => {
                  const list = Array.from(e.target.files || []);
                  const tooLarge = list.find((f) => f.size > 10 * 1024 * 1024);
                  if (tooLarge) {
                    showToast(`文件「${tooLarge.name}」超过 10MB 上限`);
                  } else {
                    setCreateFiles((prev) => [...prev, ...list].slice(0, 10));
                  }
                  e.target.value = '';
                }}
              />
            </label>
            <span style={{ fontSize: 11, color: 'var(--muted-2)' }}>
              单文件 ≤10MB，支持 png/jpg/gif/pdf/txt/log/csv/zip/json
            </span>
          </div>
          {createFiles.length > 0 ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
              {createFiles.map((f, i) => (
                <span
                  key={f.name + i}
                  className="pill soft"
                  style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}
                >
                  📎 {f.name}
                  <span
                    style={{ cursor: 'pointer', opacity: 0.6 }}
                    title="移除"
                    onClick={() => setCreateFiles((prev) => prev.filter((_, idx) => idx !== i))}
                  >
                    ✕
                  </span>
                </span>
              ))}
            </div>
          ) : null}
        </div>
      </Modal>

      <Modal
        open={backfillOpen}
        onClose={() => setBackfillOpen(false)}
        title="批量归户"
        subtitle={`已选 ${selected.length} 条未归户工单`}
      >
        <div className="field">
          <label>
            目标客户 <span className="req">*</span>
          </label>
          <select
            className="select"
            value={backfillCustomerId ?? ''}
            onChange={(e) => {
              const cid = e.target.value ? Number(e.target.value) : null;
              setBackfillCustomerId(cid);
              setBackfillContactId(null);
              setBackfillContacts([]);
              if (cid) {
                api
                  .getContacts(cid)
                  .then(setBackfillContacts)
                  .catch(() => setBackfillContacts([]));
              }
            }}
          >
            <option value="">请选择客户</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}（{c.code}）
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>联系人（选填）</label>
          <select
            className="select"
            value={backfillContactId ?? ''}
            onChange={(e) => setBackfillContactId(e.target.value ? Number(e.target.value) : null)}
            disabled={!backfillCustomerId}
          >
            <option value="">不指定</option>
            {backfillContacts.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
                {c.isPrimary ? '（主联系人）' : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="desc" style={{ marginTop: 8 }}>
          仅客户为空的工单会被更新，已挂客户的工单自动跳过。
        </div>
        <div className="modal-foot" style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
          <button className="btn ghost sm" onClick={() => setBackfillOpen(false)}>
            取消
          </button>
          <button className="btn primary sm" onClick={doBackfill} disabled={!backfillCustomerId}>
            确认归户
          </button>
        </div>
      </Modal>

      {/* V2.3 M1：相似工单组抽屉 */}
      <Modal
        open={similarOpen}
        onClose={() => setSimilarOpen(false)}
        title={similarData?.groupId ? `相似工单组 #${similarData.groupId}` : '相似工单'}
        subtitle={`共 ${similarData?.count ?? 0} 条`}
        icon="≈"
      >
        {similarLoading ? (
          <div className="empty">加载中…</div>
        ) : !similarData || similarData.count === 0 ? (
          <div className="empty">暂无相似工单</div>
        ) : (
          <>
            <div className="table-wrap" style={{ marginBottom: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 90 }}>编号</th>
                    <th>标题</th>
                    <th style={{ width: 90 }}>状态</th>
                    <th style={{ width: 76 }}>相似度</th>
                  </tr>
                </thead>
                <tbody>
                  {similarData.items.map((i) => (
                    <tr
                      key={i.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => {
                        setSimilarOpen(false);
                        navigate('/tickets/' + i.id.replace('#', ''));
                      }}
                    >
                      <td>
                        <span className="t-id">{i.id}</span>
                      </td>
                      <td>
                        <span className="t-title">{i.title}</span>
                        {i.isMain && (
                          <span className="pill soft" style={{ marginLeft: 6 }}>
                            主单
                          </span>
                        )}
                      </td>
                      <td style={{ color: 'var(--ink-2)' }}>{i.status}</td>
                      <td>{Math.round(i.score * 100)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="field">
              <label>批量回复该组</label>
              <textarea
                className="input"
                rows={4}
                value={similarReply}
                onChange={(e) => setSimilarReply(e.target.value)}
                placeholder="输入统一回复内容，将发送给组内全部工单"
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <button className="btn ghost sm" onClick={() => setSimilarOpen(false)}>
                关闭
              </button>
              <button
                className="btn primary sm"
                disabled={!similarReply.trim() || similarSending}
                onClick={sendSimilarReply}
              >
                {similarSending ? '发送中…' : `批量回复（${similarData.count}）`}
              </button>
            </div>
          </>
        )}
      </Modal>
    </>
  );
}
