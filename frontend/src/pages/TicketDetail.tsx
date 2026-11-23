import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import AIPanel from '@/components/AIPanel';
import type { Ticket, UserInfo, Triage, ReplyTemplate, SimilarGroup, TicketSummaryResult } from '@/types';

const PRIORITY_OPTIONS = [
  { v: 'P1', t: '紧急', d: '生产不可用，需立即响应' },
  { v: 'P2', t: '高', d: '核心功能受损，影响业务' },
  { v: 'P3', t: '中', d: '一般问题，可正常排期' },
  { v: 'P4', t: '低', d: '咨询 / 建议类问题' }
];

const priCls = (v: string) => (v === 'P1' ? 'p1' : v === 'P2' ? 'p2' : v === 'P3' ? 'p3' : 'p4');
const relTypeLabel = (v: string) => (v === 'duplicate' ? '重复' : v === 'parent' ? '父子' : '相关');
const slaPill = (t: string) => (t === '金牌' ? 'p2' : t === '银牌' ? 'p3' : t === '铜牌' ? 'p4' : 'soft');
const slaStateLabel = (s?: string) =>
  s === 'WARNING' ? '预警' : s === 'BREACHED' ? '已超时' : s === 'PAUSED' ? '暂停' : s === 'STOPPED'
    ? '停止' : s === 'MET' ? '已达成' : '计时中';
const l0Label = (s?: string | null) =>
  s === 'AUTO_REPLIED' ? 'AI 已自动应答' : s === 'TRANSFERRED' ? '已转人工'
    : s === 'CONFIRMED' ? '客户已确认解决' : s === 'REOPENED' ? '客户重开' : '未进入 L0';
const sentimentLabel = (s?: string | null) =>
  s === 'NEGATIVE' ? '负面' : s === 'POSITIVE' ? '正面' : '中性';

export default function TicketDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [noteText, setNoteText] = useState('');
  const [visMode, setVisMode] = useState<'customer' | 'internal'>('customer');
  const [sending, setSending] = useState(false);
  /** V2.1 M4：待随消息发送的附件 */
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);

  // 弹窗状态
  const [priOpen, setPriOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [noteModalOpen, setNoteModalOpen] = useState(false);
  const [escalateOpen, setEscalateOpen] = useState(false);
  const [priSel, setPriSel] = useState('');
  const [assigneeSel, setAssigneeSel] = useState<number | null>(null);
  const [noteModalText, setNoteModalText] = useState('');
  const [escalateReason, setEscalateReason] = useState('');
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [triage, setTriage] = useState<Triage | null>(null);
  const [triageLoading, setTriageLoading] = useState(false);

  // CSAT（代录）
  const [csatOpen, setCsatOpen] = useState(false);
  const [csatScore, setCsatScore] = useState(0);
  const [csatComment, setCsatComment] = useState('');

  // 工单关联
  const [relOpen, setRelOpen] = useState(false);
  const [relTicketId, setRelTicketId] = useState('');
  const [relType, setRelType] = useState('related');

  // 快捷回复模板
  const [templates, setTemplates] = useState<ReplyTemplate[]>([]);
  const [tplOpen, setTplOpen] = useState(false);
  const [tplName, setTplName] = useState('');
  const [tplContent, setTplContent] = useState('');
  const [tplCategory, setTplCategory] = useState('');

  // V2.3 智能服务：摘要 / 相似工单 / L0 操作
  const [summaryInfo, setSummaryInfo] = useState<TicketSummaryResult | null>(null);
  const [summaryEditing, setSummaryEditing] = useState(false);
  const [summaryDraft, setSummaryDraft] = useState('');
  const [summaryBusy, setSummaryBusy] = useState(false);
  const [similar, setSimilar] = useState<SimilarGroup | null>(null);
  const [l0Busy, setL0Busy] = useState(false);

  const load = () => {
    if (!id) return;
    setLoading(true);
    api
      .getTicket(id)
      .then((t) => setTicket(t))
      .catch((e) => showToast('加载工单失败：' + e.message))
      .finally(() => setLoading(false));
  };

  /** 静默重新拉取工单详情（不触发 loading 闪烁），操作后兜底刷新消息列表。 */
  const silentReload = () => {
    if (!id) return;
    api.getTicket(id).then(setTicket).catch(() => {});
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // 挂载时拉取回复模板（失败静默）
  useEffect(() => {
    api.listReplyTemplates().then(setTemplates).catch(() => {});
  }, []);

  // V2.3 M1：加载相似工单组
  useEffect(() => {
    if (!id) return;
    api.getTicketSimilar(id).then(setSimilar).catch(() => {});
  }, [id]);

  /** 执行工单操作：后端返回更新后的工单详情，直接刷新本地 state。 */
  const run = async (fn: () => Promise<Ticket>, okMsg: string) => {
    try {
      const t = await fn();
      setTicket(t);
      silentReload();
      showToast(okMsg);
    } catch (e) {
      showToast('操作失败：' + (e as Error).message);
    }
  };

  /** V2.1 M3：并发认领，409 时提示当前处理人 */
  const onAccept = async () => {
    try {
      const t = await api.claimTicket(id!);
      setTicket(t);
      silentReload();
      showToast('认领成功，工单已进入处理中');
    } catch (e) {
      showToast('认领失败：' + (e as Error).message);
      silentReload();
    }
  };

  // 改优先级
  const openPri = () => {
    setPriSel(ticket?.priority || 'P3');
    setPriOpen(true);
  };
  const confirmPri = () => {
    setPriOpen(false);
    run(() => api.updateTicketPriority(id!, priSel), '优先级已更新');
  };

  // 转派
  const openAssign = async () => {
    setAssigneeSel(ticket?.assigneeId ?? null);
    setAssignOpen(true);
    try {
      const list = await api.listUsers();
      setUsers(list);
    } catch (e) {
      showToast('加载用户失败：' + (e as Error).message);
    }
  };
  const confirmAssign = () => {
    if (!assigneeSel) {
      showToast('请选择目标处理人');
      return;
    }
    setAssignOpen(false);
    run(() => api.assignTicket(id!, assigneeSel), '已转派');
  };

  // 内部备注
  const openNote = () => {
    setNoteModalText('');
    setNoteModalOpen(true);
  };
  const confirmNote = () => {
    const text = noteModalText.trim();
    if (!text) return;
    setNoteModalOpen(false);
    run(() => api.addTicketMessage(id!, text, true), '内部备注已添加');
  };

  // 升级 L2：打开确认弹窗
  const openEscalate = () => {
    setEscalateReason('');
    setEscalateOpen(true);
  };
  const confirmEscalate = async () => {
    if (!ticket) return;
    setEscalateOpen(false);
    try {
      let t = ticket;
      // 升级原因作为内部备注留痕（选填）
      if (escalateReason.trim()) {
        t = await api.addTicketMessage(id!, escalateReason.trim(), true);
      }
      t = await api.escalateTicket(id!);
      setTicket(t);
      silentReload();
      showToast('已升级到 L2');
    } catch (e) {
      showToast('升级失败：' + (e as Error).message);
    }
  };

  // 详情页 composer 直接发送
  const sendMessage = async () => {
    const text = noteText.trim();
    if (!text && pendingFiles.length === 0) return;
    setSending(true);
    try {
      const ids: number[] = [];
      for (const f of pendingFiles) {
        const a = await api.uploadAttachment(id!, f, visMode === 'internal' ? 'INTERNAL' : 'CUSTOMER');
        ids.push(a.id);
      }
      const t = await api.addTicketMessage(id!, text, visMode === 'internal', ids);
      setTicket(t);
      silentReload();
      setNoteText('');
      setPendingFiles([]);
      showToast(visMode === 'internal' ? '内部备注已添加' : '回复已发送');
    } catch (e) {
      showToast('发送失败：' + (e as Error).message);
    } finally {
      setSending(false);
    }
  };

  const pickFiles = (list: FileList | null) => {
    if (!list) return;
    const files = Array.from(list);
    const tooLarge = files.find((f) => f.size > 10 * 1024 * 1024);
    if (tooLarge) {
      showToast(`文件「${tooLarge.name}」超过 10MB 上限`);
      return;
    }
    setPendingFiles((prev) => [...prev, ...files].slice(0, 10));
  };

  const downloadAttachment = async (idNum: number, name: string) => {
    try {
      await api.downloadAttachment(idNum, name);
    } catch (e) {
      showToast('下载失败：' + (e as Error).message);
    }
  };

  const handleAdopted = () => load();

  // AI 分诊建议：生成 + 一键应用
  const genTriage = async () => {
    if (!ticket) return;
    setTriageLoading(true);
    try {
      const t = await api.getTriage(ticket.id.replace('#', ''));
      setTriage(t);
    } catch (e) {
      showToast('生成建议失败：' + (e as Error).message);
    } finally {
      setTriageLoading(false);
    }
  };
  const applyTriagePriority = () => {
    if (!triage?.priority || !id) return;
    run(() => api.updateTicketPriority(id, triage.priority), '已应用建议优先级');
  };
  const applyTriageCategory = () => {
    if (!triage?.category || !id) return;
    run(() => api.updateTicketCategory(id, triage.category), '已应用建议分类');
  };
  const applyTriageAssignee = () => {
    if (!triage?.assigneeId || !id) return;
    run(() => api.assignTicket(id, triage.assigneeId!), '已应用建议处理人');
  };

  // CSAT：打开代录表单（回填已有值）
  const openCsat = () => {
    setCsatScore(ticket?.csatScore ?? 0);
    setCsatComment(ticket?.csatComment ?? '');
    setCsatOpen(true);
  };
  const submitCsat = () => {
    if (!csatScore) {
      showToast('请选择评分');
      return;
    }
    setCsatOpen(false);
    run(() => api.submitCsat(id!, csatScore, csatComment), 'CSAT 已记录');
  };

  // 工单关联：新增 / 取消
  const addRelation = async () => {
    const num = parseInt(relTicketId.replace(/\D/g, ''), 10);
    if (!num) {
      showToast('请输入有效的关联工单号');
      return;
    }
    try {
      await api.addTicketRelation(id!, num, relType);
      setRelOpen(false);
      setRelTicketId('');
      silentReload();
      showToast('已添加关联');
    } catch (e) {
      showToast('添加关联失败：' + (e as Error).message);
    }
  };
  const removeRelation = async (relationId: number) => {
    try {
      await api.deleteTicketRelation(id!, relationId);
      silentReload();
      showToast('已取消关联');
    } catch (e) {
      showToast('取消关联失败：' + (e as Error).message);
    }
  };

  // 快捷回复模板：插入（变量替换）/ 新增 / 删除
  const loadTemplates = () => api.listReplyTemplates().then(setTemplates).catch(() => {});
  const insertTemplate = (tpl: ReplyTemplate) => {
    const vars: Record<string, string> = {
      客户名: ticket?.customerName || ticket?.tenant || '客户',
      工单号: ticket?.id || ''
    };
    const text = tpl.content.replace(/\{([^}]+)\}/g, (_, k) => (k in vars ? vars[k] : '{' + k + '}'));
    setNoteText((prev) => (prev ? prev + '\n' + text : text));
    showToast('已插入模板：' + tpl.name);
  };
  const createTpl = async () => {
    if (!tplName.trim() || !tplContent.trim()) {
      showToast('请填写模板名称与内容');
      return;
    }
    try {
      await api.createReplyTemplate({
        name: tplName.trim(),
        content: tplContent,
        category: tplCategory.trim() || undefined
      });
      setTplName('');
      setTplContent('');
      setTplCategory('');
      loadTemplates();
      showToast('模板已保存');
    } catch (e) {
      showToast('保存模板失败：' + (e as Error).message);
    }
  };
  const deleteTpl = async (tplId: number) => {
    try {
      await api.deleteReplyTemplate(tplId);
      loadTemplates();
      showToast('模板已删除');
    } catch (e) {
      showToast('删除模板失败：' + (e as Error).message);
    }
  };

  // ===== V2.3 智能服务操作 =====
  const genSummary = async () => {
    if (!id) return;
    setSummaryBusy(true);
    try {
      const r = await api.getTicketSummary(id);
      setSummaryInfo(r);
      setSummaryDraft(r.summary || '');
      if (r.insufficient) showToast(r.message || '消息不足，暂不生成摘要');
    } catch (e) {
      showToast('生成摘要失败：' + (e as Error).message);
    } finally {
      setSummaryBusy(false);
    }
  };

  const saveSummary = async () => {
    if (!id) return;
    setSummaryBusy(true);
    try {
      const r = await api.updateTicketSummary(id, summaryDraft);
      setSummaryInfo(r);
      setSummaryEditing(false);
      showToast('摘要已保存');
      silentReload();
    } catch (e) {
      showToast('保存摘要失败：' + (e as Error).message);
    } finally {
      setSummaryBusy(false);
    }
  };

  const doL0 = async (action: 'reopen' | 'transfer') => {
    if (!id || l0Busy) return;
    setL0Busy(true);
    try {
      if (action === 'reopen') {
        await api.reopenL0(id);
      } else {
        await api.transferL0(id);
      }
      showToast(action === 'reopen' ? '工单已重开' : '已转人工处理');
      load();
    } catch (e) {
      showToast('操作失败：' + (e as Error).message);
    } finally {
      setL0Busy(false);
    }
  };

  const doSentimentUpgrade = async () => {
    if (!id) return;
    try {
      const r = await api.upgradeSentiment(id);
      showToast(r.upgraded ? '已升级为 P2' : `当前优先级 ${r.priority}，无需升级`);
      load();
    } catch (e) {
      showToast('升级失败：' + (e as Error).message);
    }
  };

  if (loading) return <div className="empty">加载中…</div>;
  if (!ticket) return <div className="empty">工单不存在</div>;

  const sourceLabel = ticket.id.startsWith('#41') ? 'GitHub Issue 同步' : '手工创建';
  const audit = [
    { t: ticket.updateTime, who: ticket.assigneeName || '系统', act: '工单状态更新为「' + ticket.status + '」' },
    ...((ticket.messages || [])
      .slice(-3)
      .reverse()
      .map((m) => ({
        t: ticket.updateTime,
        who: m.who,
        act: (m.internal ? '添加内部备注' : '回复消息') + '：' + m.text.slice(0, 24)
      })))
  ];

  const summaryText = summaryInfo?.summary || ticket.summary || '';

  return (
    <>
      <div className="action-bar">
        <span className="ab-id">{ticket.id}</span>
        <span className={'pill ' + ticket.priorityClass}>{ticket.priority}</span>
        <span className={'pill ' + ticket.statusClass}>{ticket.status}</span>
        <span className="ab-title">{ticket.title}</span>
        <span className="ab-sla" style={{ color: ticket.slaColor }}>
          {ticket.sla}
        </span>
        <div className="ab-actions">
          <button className="btn primary sm" onClick={onAccept}>
            接单
          </button>
          <button className="btn sm" onClick={openAssign}>
            转派
          </button>
          <button className="btn sm" onClick={openPri}>
            改优先级
          </button>
          <button className="btn sm" onClick={openEscalate}>
            升级 L2
          </button>
          <button className="btn sm" onClick={openNote}>
            备注
          </button>
        </div>
      </div>

      <div className="detail-top">
        <button className="back" onClick={() => navigate('/')}>
          ‹ 返回工单列表
        </button>
        <div className="badge-row">
          <span className="pill soft">{ticket.id}</span>
          <span className={'pill ' + ticket.priorityClass}>{ticket.priority}</span>
          <span className={'pill ' + ticket.statusClass}>{ticket.status}</span>
          <span className="pill soft">{ticket.category}</span>
        </div>
        <div className="detail-title">{ticket.title}</div>
        <div className="meta-grid">
          <div className="meta-item">
            <div className="meta-k">客户</div>
            <div className="meta-v">{ticket.customerName || ticket.tenant}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">产品</div>
            <div className="meta-v">{ticket.product}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">环境</div>
            <div className="meta-v">{ticket.env || '—'}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">SLA</div>
            <div className="meta-v" style={{ color: ticket.slaColor }}>
              {ticket.sla}
              {ticket.slaState ? (
                <span style={{ display: 'block', fontSize: 11, color: 'var(--muted-2)', marginTop: 2 }}>
                  {slaStateLabel(ticket.slaState)}
                  {ticket.slaResolveDueAt ? ' · 到期 ' + ticket.slaResolveDueAt : ''}
                </span>
              ) : null}
            </div>
          </div>
          <div className="meta-item">
            <div className="meta-k">负责人</div>
            <div className="meta-v">{ticket.assigneeName || '未分配'}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">创建时间</div>
            <div className="meta-v">{ticket.createdAt || '—'}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">首次响应</div>
            <div className="meta-v">{ticket.firstResponseAt || '—'}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">解决时间</div>
            <div className="meta-v">{ticket.resolvedAt || '—'}</div>
          </div>
        </div>
      </div>

      <div className="detail-grid">
        <div className="col-left">
          <div className="card" style={{ overflow: 'hidden' }}>
            <div className="panel-head">
              <span className="panel-title">工单内容</span>
              <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>来源：{sourceLabel}</span>
            </div>
            <div className="panel-body">
              <div className="ticket-body">{ticket.body}</div>
              {ticket.code ? (
                <div className="code-block">
                  <div className="cb-head">
                    <span className="dot"></span>
                    <span>application.yml</span>
                  </div>
                  {ticket.code}
                </div>
              ) : null}

              <div className="thread">
                <div className="thread-head">
                  <span className="t">会话记录</span>
                  <span className="s">{ticket.messages?.length || 0} 条</span>
                </div>
                {ticket.messages && ticket.messages.length > 0 ? (
                  ticket.messages.map((m, i) => (
                    <div className={'msg' + (m.internal ? ' internal' : '')} key={i}>
                      <div
                        className="avatar"
                        style={{
                          background: m.internal ? '#fef3c7' : '#dbeafe',
                          color: m.internal ? '#b45309' : '#1d4ed8'
                        }}
                      >
                        {m.who.slice(0, 1)}
                      </div>
                      <div className="msg-body">
                        <div className="msg-meta">
                          <b>{m.who}</b>
                          <span className="role-tag">{m.role}</span>
                        </div>
                        <div className="msg-text">{m.text}</div>
                        {m.attachments && m.attachments.length > 0 ? (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
                            {m.attachments.map((a) => (
                              <button
                                key={a.id}
                                className="btn ghost sm"
                                title={'下载 ' + a.fileName}
                                onClick={() => downloadAttachment(a.id, a.fileName)}
                              >
                                📎 {a.fileName}（{a.sizeText}）
                              </button>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  ))
                ) : (
                  <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无会话记录</div>
                )}
                {ticket.attachments && ticket.attachments.length > 0 ? (
                  <div style={{ marginTop: 12 }}>
                    <div style={{ fontSize: 12, color: 'var(--muted-2)', marginBottom: 6 }}>工单附件</div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {ticket.attachments.map((a) => (
                        <button
                          key={a.id}
                          className="btn ghost sm"
                          title={'下载 ' + a.fileName}
                          onClick={() => downloadAttachment(a.id, a.fileName)}
                        >
                          📎 {a.fileName}（{a.sizeText}）
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>

              <div className="composer">
                <div className="seg">
                  <button
                    className={'seg-item' + (visMode === 'customer' ? ' active' : '')}
                    onClick={() => setVisMode('customer')}
                  >
                    回复客户
                  </button>
                  <button
                    className={'seg-item' + (visMode === 'internal' ? ' active' : '')}
                    onClick={() => setVisMode('internal')}
                  >
                    内部备注
                  </button>
                </div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <select
                    className="input"
                    style={{ height: 28, minWidth: 160, flex: 1 }}
                    value=""
                    onChange={(e) => {
                      const t = templates.find((x) => x.id === Number(e.target.value));
                      if (t) insertTemplate(t);
                      e.target.value = '';
                    }}
                  >
                    <option value="">插入模板…</option>
                    {templates.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                  <button className="btn sm" onClick={() => setTplOpen((v) => !v)}>
                    {tplOpen ? '收起管理' : '管理模板'}
                  </button>
                </div>
                {tplOpen && (
                  <div
                    style={{
                      border: '1px solid var(--border)',
                      borderRadius: 6,
                      padding: 10,
                      margin: '8px 0'
                    }}
                  >
                    <div style={{ fontSize: 12, color: 'var(--muted-2)', marginBottom: 6 }}>
                      已有模板（点击 ✕ 删除）
                    </div>
                    {templates.length === 0 ? (
                      <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无模板</div>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 10 }}>
                        {templates.map((t) => (
                          <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontSize: 12, flex: 1 }}>
                              {t.name}
                              {t.category ? (
                                <span style={{ color: 'var(--muted-2)' }}> · {t.category}</span>
                              ) : null}
                            </span>
                            <button className="btn sm danger" onClick={() => deleteTpl(t.id)}>
                              ✕
                            </button>
                          </div>
                        ))}
                      </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <input
                          className="input"
                          style={{ height: 28, flex: 1 }}
                          placeholder="模板名称"
                          value={tplName}
                          onChange={(e) => setTplName(e.target.value)}
                        />
                        <input
                          className="input"
                          style={{ height: 28, width: 120 }}
                          placeholder="分类（选填）"
                          value={tplCategory}
                          onChange={(e) => setTplCategory(e.target.value)}
                        />
                      </div>
                      <textarea
                        className="input"
                        style={{ minHeight: 56 }}
                        placeholder="模板内容，支持 {客户名} / {工单号} 变量"
                        value={tplContent}
                        onChange={(e) => setTplContent(e.target.value)}
                      />
                      <div>
                        <button className="btn primary sm" onClick={createTpl}>
                          保存为模板
                        </button>
                      </div>
                    </div>
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 8 }}>
                  <label className="btn ghost sm" style={{ cursor: 'pointer' }}>
                    添加附件
                    <input
                      type="file"
                      multiple
                      style={{ display: 'none' }}
                      onChange={(e) => {
                        pickFiles(e.target.files);
                        e.target.value = '';
                      }}
                    />
                  </label>
                  <span style={{ fontSize: 11, color: 'var(--muted-2)' }}>
                    单文件 ≤10MB：png/jpg/jpeg/gif/pdf/txt/log/csv/zip/json
                  </span>
                </div>
                {pendingFiles.length > 0 ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, margin: '6px 0' }}>
                    {pendingFiles.map((f, i) => (
                      <span key={f.name + i} className="pill soft" style={{ display: 'inline-flex', gap: 6 }}>
                        {f.name}
                        <span
                          style={{ cursor: 'pointer' }}
                          title="移除"
                          onClick={() => setPendingFiles((prev) => prev.filter((_, idx) => idx !== i))}
                        >
                          ✕
                        </span>
                      </span>
                    ))}
                  </div>
                ) : null}
                <textarea
                  value={noteText}
                  onChange={(e) => setNoteText(e.target.value)}
                  onKeyDown={(e) => {
                    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') sendMessage();
                  }}
                  placeholder={
                    visMode === 'internal'
                      ? '输入内部备注（仅内部可见，客户看不到）…'
                      : '输入回复内容…（Enter 换行，⌘/Ctrl + Enter 发送）'
                  }
                />
                <div className="composer-foot">
                  <span className={'hint' + (visMode === 'internal' ? ' warn' : '')}>
                    {visMode === 'internal'
                      ? '⚠ 内部备注不会发送给客户，仅团队内可见'
                      : '将以你的身份发送给客户，建议先由 AI 生成草稿后审核'}
                  </span>
                  <button
                    className="btn primary sm"
                    onClick={sendMessage}
                    disabled={sending || (!noteText.trim() && pendingFiles.length === 0)}
                  >
                    {visMode === 'internal' ? '保存备注' : '发送'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-right">
          {/* V2.3 M6：会话摘要 */}
          <div className="card">
            <div className="panel-head">
              <span className="panel-title">会话摘要</span>
              <button
                className="btn sm"
                style={{ marginLeft: 'auto' }}
                onClick={genSummary}
                disabled={summaryBusy}
              >
                {summaryBusy ? '生成中…' : summaryText ? '刷新' : '生成摘要'}
              </button>
            </div>
            <div className="panel-body">
              {summaryEditing ? (
                <>
                  <textarea
                    className="input"
                    rows={8}
                    value={summaryDraft}
                    onChange={(e) => setSummaryDraft(e.target.value)}
                  />
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8 }}>
                    <button className="btn ghost sm" onClick={() => setSummaryEditing(false)}>
                      取消
                    </button>
                    <button
                      className="btn primary sm"
                      onClick={saveSummary}
                      disabled={summaryBusy || !summaryDraft.trim()}
                    >
                      保存
                    </button>
                  </div>
                </>
              ) : summaryText ? (
                <>
                  <div style={{ whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.7 }}>
                    {summaryText}
                  </div>
                  <button
                    className="btn ghost sm"
                    style={{ marginTop: 8 }}
                    onClick={() => {
                      setSummaryDraft(summaryText);
                      setSummaryEditing(true);
                    }}
                  >
                    修正摘要
                  </button>
                </>
              ) : (
                <div style={{ fontSize: 12, color: 'var(--muted-2)', lineHeight: 1.6 }}>
                  {summaryInfo?.insufficient
                    ? summaryInfo.message
                    : '会话消息达到 10 条后自动生成摘要，也可手动生成。'}
                </div>
              )}
            </div>
          </div>

          {/* V2.3 M4：L0 自助处理状态 */}
          {ticket.l0State && (
            <div className="card">
              <div className="panel-head">
                <span className="panel-title">L0 自助处理</span>
              </div>
              <div className="panel-body">
                <div className="kv">
                  <span className="k">状态</span>
                  <span className="v">{l0Label(ticket.l0State)}</span>
                </div>
                <div className="kv">
                  <span className="k">重开次数</span>
                  <span className="v">{ticket.l0ReopenCount || 0}</span>
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                  <button className="btn sm" onClick={() => doL0('reopen')} disabled={l0Busy}>
                    重开
                  </button>
                  <button className="btn sm" onClick={() => doL0('transfer')} disabled={l0Busy}>
                    转人工
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* V2.3 M5：客户情绪 */}
          {ticket.sentiment && (
            <div className="card">
              <div className="panel-head">
                <span className="panel-title">客户情绪</span>
              </div>
              <div className="panel-body">
                <div className="kv">
                  <span className="k">最近情绪</span>
                  <span className="v">
                    <span
                      className="pill soft"
                      style={
                        ticket.sentiment === 'NEGATIVE'
                          ? { background: '#fee2e2', color: '#b91c1c' }
                          : undefined
                      }
                    >
                      {sentimentLabel(ticket.sentiment)}
                    </span>
                  </span>
                </div>
                {ticket.sentiment === 'NEGATIVE' && (
                  <button className="btn sm" style={{ marginTop: 8 }} onClick={doSentimentUpgrade}>
                    升级为 P2
                  </button>
                )}
              </div>
            </div>
          )}

          {/* V2.3 M1：相似工单 */}
          {similar && similar.count > 1 && (
            <div className="card">
              <div className="panel-head">
                <span className="panel-title">相似工单</span>
                <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--muted-2)' }}>
                  共 {similar.count} 条
                </span>
              </div>
              <div className="panel-body">
                {similar.items.map((i) => (
                  <div
                    key={i.id}
                    className="kv"
                    style={{ cursor: 'pointer' }}
                    onClick={() => navigate('/tickets/' + i.id.replace('#', ''))}
                  >
                    <span className="k">{i.id}</span>
                    <span
                      className="v"
                      style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                    >
                      {i.title}
                      {i.isMain ? ' · 主单' : ` · ${Math.round(i.score * 100)}%`}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <AIPanel ticketId={ticket.id.replace('#', '')} onAdopted={handleAdopted} />

          <div className="card">
            <div className="panel-head">
              <span className="panel-title">AI 分诊建议</span>
              <button
                className="btn sm"
                style={{ marginLeft: 'auto' }}
                onClick={genTriage}
                disabled={triageLoading}
              >
                {triageLoading ? '生成中…' : triage ? '重新生成' : '生成建议'}
              </button>
            </div>
            <div className="panel-body">
              {!triage ? (
                <div style={{ fontSize: 12, color: 'var(--muted-2)', lineHeight: 1.6 }}>
                  基于工单内容与当前处理人负载，AI 给出优先级 / 分类 / 处理人建议，可一键应用。
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 12, color: 'var(--muted-2)', width: 80 }}>建议优先级</span>
                    <span style={{ flex: 1, fontWeight: 600 }}>{triage.priority}</span>
                    <button className="btn sm" onClick={applyTriagePriority}>
                      应用
                    </button>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 12, color: 'var(--muted-2)', width: 80 }}>建议分类</span>
                    <span style={{ flex: 1 }}>{triage.category}</span>
                    <button className="btn sm" onClick={applyTriageCategory}>
                      应用
                    </button>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 12, color: 'var(--muted-2)', width: 80 }}>建议处理人</span>
                    <span style={{ flex: 1 }}>
                      {triage.assigneeName || (triage.assigneeId ? '#' + triage.assigneeId : '—')}
                    </span>
                    <button className="btn sm" onClick={applyTriageAssignee} disabled={!triage.assigneeId}>
                      应用
                    </button>
                  </div>
                  {triage.reasoning && (
                    <div style={{ fontSize: 11, color: 'var(--muted-2)', marginTop: 4, lineHeight: 1.6 }}>
                      理由：{triage.reasoning}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="panel-head">
              <span className="panel-title">客户信息</span>
              {ticket.customerId ? (
                <button
                  className="btn sm"
                  style={{ marginLeft: 'auto' }}
                  onClick={() => navigate('/customers/' + ticket.customerId)}
                >
                  客户 360 →
                </button>
              ) : null}
            </div>
            <div className="panel-body">
              <div className="kv">
                <span className="k">客户</span>
                <span className="v">{ticket.customerName || ticket.tenant}</span>
              </div>
              {ticket.customerCode ? (
                <div className="kv">
                  <span className="k">客户编码</span>
                  <span className="v">{ticket.customerCode}</span>
                </div>
              ) : null}
              {ticket.slaTier ? (
                <div className="kv">
                  <span className="k">服务等级</span>
                  <span className="v">
                    <span className={'pill ' + slaPill(ticket.slaTier)}>{ticket.slaTier}</span>
                  </span>
                </div>
              ) : null}
              {ticket.contactName ? (
                <div className="kv">
                  <span className="k">联系人</span>
                  <span className="v">
                    {ticket.contactName}
                    {ticket.contactIsPrimary ? ' ★' : ''}
                  </span>
                </div>
              ) : null}
              {ticket.contactEmail ? (
                <div className="kv">
                  <span className="k">联系邮箱</span>
                  <span className="v">{ticket.contactEmail}</span>
                </div>
              ) : null}
              <div className="kv">
                <span className="k">产品版本</span>
                <span className="v">{ticket.product}</span>
              </div>
              <div className="kv">
                <span className="k">部署环境</span>
                <span className="v">{ticket.env || '未填写'}</span>
              </div>
              <div className="kv">
                <span className="k">优先级</span>
                <span className="v">{ticket.priority}</span>
              </div>
              <div className="kv">
                <span className="k">分类</span>
                <span className="v">{ticket.category}</span>
              </div>
            </div>
          </div>

          <div className="card">
            <div className="panel-head">
              <span className="panel-title">关联工单</span>
              <button className="btn sm" style={{ marginLeft: 'auto' }} onClick={() => setRelOpen((v) => !v)}>
                {relOpen ? '取消' : '新增关联'}
              </button>
            </div>
            <div className="panel-body">
              {!ticket.relations || ticket.relations.length === 0 ? (
                <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无关联工单</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {ticket.relations.map((r) => (
                    <div key={r.relationId} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
                      <button
                        className="link"
                        style={{ fontWeight: 600, padding: 0 }}
                        onClick={() => navigate('/tickets/' + r.id.replace('#', ''))}
                      >
                        {r.id}
                      </button>
                      <span
                        style={{
                          flex: 1,
                          color: 'var(--ink-2)',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {r.title}
                      </span>
                      <span className="pill soft" style={{ fontSize: 11 }}>
                        {relTypeLabel(r.relationType)}
                      </span>
                      <button className="btn sm" onClick={() => removeRelation(r.relationId)}>
                        取消
                      </button>
                    </div>
                  ))}
                </div>
              )}
              {relOpen && (
                <div
                  style={{
                    marginTop: 10,
                    borderTop: '1px solid var(--border)',
                    paddingTop: 10,
                    display: 'flex',
                    gap: 6,
                    flexWrap: 'wrap'
                  }}
                >
                  <input
                    className="input"
                    style={{ height: 28, width: 120 }}
                    placeholder="关联工单号 #…"
                    value={relTicketId}
                    onChange={(e) => setRelTicketId(e.target.value)}
                  />
                  <select
                    className="input"
                    style={{ height: 28, width: 100 }}
                    value={relType}
                    onChange={(e) => setRelType(e.target.value)}
                  >
                    <option value="related">相关</option>
                    <option value="duplicate">重复</option>
                    <option value="parent">父子</option>
                  </select>
                  <button className="btn primary sm" onClick={addRelation}>
                    添加
                  </button>
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="panel-head">
              <span className="panel-title">客户满意度</span>
              <button className="btn sm" style={{ marginLeft: 'auto' }} onClick={openCsat}>
                {ticket.csatScore ? '重新评价' : '录入评价'}
              </button>
            </div>
            <div className="panel-body">
              {ticket.csatScore ? (
                <>
                  <div style={{ fontSize: 20, letterSpacing: 2 }}>
                    {[1, 2, 3, 4, 5].map((s) => (
                      <span key={s} style={{ color: s <= ticket.csatScore! ? '#f59e0b' : '#cbd5e1' }}>
                        ★
                      </span>
                    ))}
                    <span style={{ fontSize: 13, color: 'var(--muted-2)', marginLeft: 8 }}>
                      {ticket.csatScore}/5
                    </span>
                  </div>
                  {ticket.csatComment && (
                    <div
                      style={{
                        fontSize: 12,
                        color: 'var(--ink-2)',
                        marginTop: 6,
                        whiteSpace: 'pre-wrap'
                      }}
                    >
                      {ticket.csatComment}
                    </div>
                  )}
                  <div style={{ fontSize: 11, color: 'var(--muted-2)', marginTop: 4 }}>
                    评价于 {ticket.csatAt}
                  </div>
                </>
              ) : (
                <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无客户评价，可代录 1-5 分满意度。</div>
              )}
              {csatOpen && (
                <div style={{ marginTop: 10, borderTop: '1px solid var(--border)', paddingTop: 10 }}>
                  <div style={{ fontSize: 20, letterSpacing: 4 }}>
                    {[1, 2, 3, 4, 5].map((s) => (
                      <span
                        key={s}
                        style={{ color: s <= csatScore ? '#f59e0b' : '#cbd5e1', cursor: 'pointer' }}
                        onClick={() => setCsatScore(s)}
                      >
                        ★
                      </span>
                    ))}
                  </div>
                  <textarea
                    className="input"
                    style={{ minHeight: 56, marginTop: 8 }}
                    placeholder="评语（选填）"
                    value={csatComment}
                    onChange={(e) => setCsatComment(e.target.value)}
                  />
                  <div style={{ marginTop: 8 }}>
                    <button className="btn primary sm" onClick={submitCsat}>
                      提交
                    </button>
                    <button
                      className="btn sm"
                      style={{ marginLeft: 6 }}
                      onClick={() => setCsatOpen(false)}
                    >
                      取消
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="panel-head">
              <span className="panel-title">操作记录</span>
            </div>
            <div className="panel-body">
              <div className="audit">
                {audit.map((a, i) => (
                  <div className="au" key={i}>
                    <b>{a.who}</b> · {a.act}
                    <div className="t">{a.t}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 改优先级弹窗 */}
      <Modal
        open={priOpen}
        onClose={() => setPriOpen(false)}
        title="修改优先级"
        subtitle={`当前：${ticket.priority}`}
        icon="!"
        iconClass="warn"
        footer={
          <>
            <span className="note">优先级变更会同步影响 SLA 倒计时</span>
            <button className="btn ghost sm" onClick={() => setPriOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmPri}>
              确认修改
            </button>
          </>
        }
      >
        <div className="pri-grid">
          {PRIORITY_OPTIONS.map((o) => (
            <div
              key={o.v}
              className={'pri-opt' + (priSel === o.v ? ' sel' : '')}
              onClick={() => setPriSel(o.v)}
            >
              <div className="h">
                <span className={'pill ' + priCls(o.v)}>{o.v}</span>
                {o.t}
              </div>
              <div className="d">{o.d}</div>
            </div>
          ))}
        </div>
      </Modal>

      {/* 转派弹窗 */}
      <Modal
        open={assignOpen}
        onClose={() => setAssignOpen(false)}
        title="转派工单"
        subtitle={`当前处理人：${ticket.assigneeName || '未分配'}`}
        icon="↔"
        footer={
          <>
            <span className="note">转派后系统将自动留一条内部备注</span>
            <button className="btn ghost sm" onClick={() => setAssignOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmAssign}>
              确认转派
            </button>
          </>
        }
      >
        <div className="opt-list">
          {users.length === 0 ? (
            <div style={{ color: 'var(--muted)', fontSize: 12, textAlign: 'center', padding: '20px 0' }}>
              加载用户列表中…
            </div>
          ) : (
            users.map((u) => (
              <div
                key={u.id}
                className={'opt' + (assigneeSel === u.id ? ' sel' : '')}
                onClick={() => setAssigneeSel(u.id)}
              >
                <input type="radio" readOnly checked={assigneeSel === u.id} />
                <span className="ok">{assigneeSel === u.id ? '✓' : ''}</span>
                <div className="opt-main">
                  <div className="opt-t">{u.name}</div>
                  <div className="opt-d">@{u.username}</div>
                </div>
                <div className="opt-meta">
                  {(u.role || '').toUpperCase() === 'ADMIN' ? '管理员' : '支持工程师'}
                </div>
              </div>
            ))
          )}
        </div>
      </Modal>

      {/* 内部备注弹窗 */}
      <Modal
        open={noteModalOpen}
        onClose={() => setNoteModalOpen(false)}
        title="添加内部备注"
        subtitle="仅团队内可见，客户无法查看"
        icon="✎"
        iconClass="warn"
        footer={
          <>
            <span className="note warn">内部备注不会发送给客户</span>
            <button className="btn ghost sm" onClick={() => setNoteModalOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmNote} disabled={!noteModalText.trim()}>
              保存备注
            </button>
          </>
        }
      >
        <div className="field">
          <label>
            备注内容 <span className="req">*</span>
          </label>
          <textarea
            className="textarea"
            autoFocus
            value={noteModalText}
            onChange={(e) => setNoteModalText(e.target.value)}
            placeholder="例如：怀疑是 3.3 起 probes 需要显式开启，且 readiness group 默认不含自定义 HealthIndicator。"
          />
        </div>
      </Modal>

      {/* 升级 L2 确认弹窗 */}
      <Modal
        open={escalateOpen}
        onClose={() => setEscalateOpen(false)}
        title="升级到 L2"
        subtitle={`当前状态：${ticket.status} · ${ticket.priority}`}
        icon="↑"
        iconClass="warn"
        footer={
          <>
            <span className="note warn">升级后状态变为「已升级」，将通知 L2 值班工程师</span>
            <button className="btn ghost sm" onClick={() => setEscalateOpen(false)}>
              取消
            </button>
            <button className="btn danger sm" onClick={confirmEscalate}>
              确认升级
            </button>
          </>
        }
      >
        <div className="callout info" style={{ marginBottom: 14 }}>
          <span className="ico">ℹ</span>
          <div>
            <b>升级影响：</b>工单状态将变更为「已升级」，SLA 重新计时，并自动通知 L2 二线支持。请确认当前排查已无法在 L1 解决。
          </div>
        </div>
        <div className="field">
          <label>升级原因（选填，将作为内部备注留痕）</label>
          <textarea
            className="textarea"
            autoFocus
            value={escalateReason}
            onChange={(e) => setEscalateReason(e.target.value)}
            placeholder="例如：已尝试重建索引与重启，问题复现，怀疑是内核级缺陷，需 L2 介入定位。"
          />
        </div>
      </Modal>
    </>
  );
}
