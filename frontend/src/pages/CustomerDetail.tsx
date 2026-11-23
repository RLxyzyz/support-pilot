import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { CustomerDetail, Contact, Ticket, CustomerHealth, SentimentTrendPoint } from '@/types';
import { maskEmail, maskPhone } from '@/utils/mask';

const PAGE_SIZES = [10, 20, 50];
const slaPill = (t: string) => (t === '金牌' ? 'p2' : t === '银牌' ? 'p3' : t === '铜牌' ? 'p4' : 'soft');

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const cid = id ? Number(id) : 0;

  const [data, setData] = useState<CustomerDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  // 联系人弹窗
  const [contactOpen, setContactOpen] = useState(false);
  const [editingContact, setEditingContact] = useState<Contact | null>(null);
  const [cName, setCName] = useState('');
  const [cEmail, setCEmail] = useState('');
  const [cPhone, setCPhone] = useState('');
  const [cTitle, setCTitle] = useState('');
  const [cPrimary, setCPrimary] = useState(false);
  const [saving, setSaving] = useState(false);

  // V2.3 智能服务：健康分 + 情绪趋势
  const [health, setHealth] = useState<CustomerHealth | null>(null);
  const [sentimentTrend, setSentimentTrend] = useState<SentimentTrendPoint[]>([]);

  useEffect(() => {
    if (!cid) return;
    api.getCustomerHealth(cid).then(setHealth).catch(() => {});
    api.getCustomerSentimentTrend(cid).then(setSentimentTrend).catch(() => {});
  }, [cid]);

  const load = async () => {
    if (!cid) return;
    setLoading(true);
    try {
      const d = await api.getCustomer(cid, page, size);
      setData(d);
    } catch (e) {
      showToast('加载客户详情失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cid, page, size]);

  if (loading) return <div className="empty">加载中…</div>;
  if (!data) return <div className="empty">客户不存在</div>;

  const { customer, contacts, tickets, recentTickets, avgCsat, csatCount, slaRate, resolvedCount, ticketTotal } = data;
  const totalPages = Math.max(Math.ceil(tickets.total / size), 1);

  const openTicket = (t: Ticket) => navigate('/tickets/' + t.id.replace('#', ''));

  const openCreateContact = () => {
    setEditingContact(null);
    setCName('');
    setCEmail('');
    setCPhone('');
    setCTitle('');
    setCPrimary(contacts.length === 0);
    setContactOpen(true);
  };

  const openEditContact = (c: Contact) => {
    setEditingContact(c);
    setCName(c.name);
    setCEmail(c.email || '');
    setCPhone(c.phone || '');
    setCTitle(c.title || '');
    setCPrimary(!!c.isPrimary);
    setContactOpen(true);
  };

  const confirmContact = async () => {
    if (!cName.trim()) {
      showToast('请填写联系人姓名');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: cName.trim(),
        email: cEmail.trim() || undefined,
        phone: cPhone.trim() || undefined,
        title: cTitle.trim() || undefined,
        isPrimary: cPrimary
      };
      if (editingContact) {
        await api.updateContact(cid, editingContact.id, payload);
        showToast('联系人已更新');
      } else {
        await api.createContact(cid, payload);
        showToast('联系人已添加');
      }
      setContactOpen(false);
      load();
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const doSetPrimary = async (c: Contact) => {
    try {
      await api.setPrimaryContact(cid, c.id);
      showToast(`已设「${c.name}」为主联系人`);
      load();
    } catch (e) {
      showToast('操作失败：' + (e as Error).message);
    }
  };

  const doDeleteContact = async (c: Contact) => {
    if (!window.confirm(`确认删除联系人「${c.name}」？`)) return;
    try {
      await api.deleteContact(cid, c.id);
      showToast('联系人已删除');
      load();
    } catch (e) {
      showToast('删除失败：' + (e as Error).message);
    }
  };

  return (
    <>
      <div className="detail-top">
        <button className="back" onClick={() => navigate('/customers')}>
          ‹ 返回客户列表
        </button>
        <div className="badge-row">
          <span className="pill soft">{customer.code}</span>
          <span className={'pill ' + slaPill(customer.slaTier)}>{customer.slaTier}</span>
          <span className={'pill ' + (customer.status === 'active' ? 'st-res' : 'soft')}>
            {customer.status === 'active' ? '启用' : '停用'}
          </span>
        </div>
        <div className="detail-title">{customer.name}</div>
        <div className="meta-grid">
          <div className="meta-item">
            <div className="meta-k">客户编码</div>
            <div className="meta-v">{customer.code}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">服务等级</div>
            <div className="meta-v">{customer.slaTier}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">合同编号</div>
            <div className="meta-v">{customer.contractNo || '—'}</div>
          </div>
          <div className="meta-item">
            <div className="meta-k">合同周期</div>
            <div className="meta-v">
              {customer.contractStart || '—'} ~ {customer.contractEnd || '—'}
            </div>
          </div>
          <div className="meta-item">
            <div className="meta-k">联系人</div>
            <div className="meta-v">{contacts.length} 位</div>
          </div>
        </div>
      </div>

      {/* 360 统计卡片 */}
      <div className="kpi-grid">
        <div className="card kpi">
          <div className="kpi-label">工单总数</div>
          <div className="kpi-val">
            {ticketTotal}
            <span>条</span>
          </div>
        </div>
        <div className="card kpi">
          <div className="kpi-label">平均 CSAT</div>
          <div className="kpi-val">
            {avgCsat ? avgCsat.toFixed(1) : '—'}
            <span>/ 5 · {csatCount} 条评价</span>
          </div>
        </div>
        <div className="card kpi">
          <div className="kpi-label">SLA 达成率</div>
          <div className="kpi-val">
            {resolvedCount ? slaRate.toFixed(0) : '—'}
            <span>% · {resolvedCount} 条已解决</span>
          </div>
        </div>
        <div className="card kpi">
          <div className="kpi-label">联系人</div>
          <div className="kpi-val">
            {contacts.length}
            <span>位</span>
          </div>
        </div>
      </div>

      <div className="detail-grid">
        <div className="col-left">
          {/* 工单分页列表 */}
          <div className="card">
            <div className="panel-head">
              <span className="panel-title">关联工单</span>
              <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 8 }}>
                共 {tickets.total} 条 · 点击跳转工单详情
              </span>
            </div>
            <div className="table-wrap" style={{ border: 'none', borderRadius: 0 }}>
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 78 }}>编号</th>
                    <th>标题</th>
                    <th style={{ width: 70 }}>优先级</th>
                    <th style={{ width: 88 }}>状态</th>
                    <th style={{ width: 110 }}>更新时间</th>
                    <th style={{ width: 60 }}>CSAT</th>
                  </tr>
                </thead>
                <tbody>
                  {tickets.list.length === 0 ? (
                    <tr>
                      <td colSpan={6}>
                        <div className="empty">暂无关联工单</div>
                      </td>
                    </tr>
                  ) : (
                    tickets.list.map((t) => (
                      <tr key={t.id} onClick={() => openTicket(t)}>
                        <td>
                          <span className="t-id">{t.id}</span>
                        </td>
                        <td>
                          <span className="t-title">{t.title}</span>
                          <span className="t-sub">{t.category}</span>
                        </td>
                        <td>
                          <span className={'pill ' + t.priorityClass}>{t.priority}</span>
                        </td>
                        <td>
                          <span className={'pill ' + t.statusClass}>{t.status}</span>
                        </td>
                        <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>{t.updateTime}</td>
                        <td style={{ color: t.csatScore ? '#f59e0b' : 'var(--muted-2)' }}>
                          {t.csatScore ? t.csatScore + '★' : '—'}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="list-foot" style={{ borderTop: '1px solid var(--line)' }}>
              <div>
                共 <b style={{ color: 'var(--ink)' }}>{tickets.total}</b> 条 · 第{' '}
                <b style={{ color: 'var(--ink)' }}>{page}</b> / {totalPages} 页
              </div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                每页
                <select
                  value={size}
                  onChange={(e) => {
                    setSize(Number(e.target.value));
                    setPage(1);
                  }}
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
                <button className="pg" disabled={page >= totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>
                  ›
                </button>
              </div>
            </div>
          </div>

          {/* 最近 5 单 */}
          {recentTickets.length > 0 ? (
            <div className="card" style={{ marginTop: 14 }}>
              <div className="panel-head">
                <span className="panel-title">最近工单</span>
                <span style={{ fontSize: 11, color: 'var(--muted-2)', marginLeft: 'auto' }}>最近 {recentTickets.length} 条</span>
              </div>
              <div className="panel-body">
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {recentTickets.map((t) => (
                    <div
                      key={t.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        fontSize: 12,
                        cursor: 'pointer'
                      }}
                      onClick={() => openTicket(t)}
                    >
                      <span className="t-id">{t.id}</span>
                      <span
                        className={'pill ' + t.statusClass}
                        style={{ flex: '0 0 auto' }}
                      >
                        {t.status}
                      </span>
                      <span
                        style={{
                          flex: 1,
                          color: 'var(--ink-2)',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {t.title}
                      </span>
                      <span style={{ color: 'var(--muted-2)', fontSize: 11 }}>{t.updateTime}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : null}
        </div>

        <div className="col-right">
          {/* V2.3 M7：客户健康分 */}
          <div className="card">
            <div className="panel-head">
              <span className="panel-title">客户健康分</span>
              {health ? (
                <span
                  className="pill soft"
                  style={{ marginLeft: 'auto' }}
                >
                  {health.level}
                </span>
              ) : null}
            </div>
            <div className="panel-body">
              {!health ? (
                <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无健康分数据</div>
              ) : (
                <>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                    <svg width="108" height="108" viewBox="0 0 110 110">
                      <circle cx="55" cy="55" r="42" fill="none" stroke="#eef2f7" strokeWidth="10" />
                      <circle
                        cx="55"
                        cy="55"
                        r="42"
                        fill="none"
                        stroke={
                          health.score >= 80 ? '#16a34a' : health.score >= 60 ? '#d97706' : '#dc2626'
                        }
                        strokeWidth="10"
                        strokeLinecap="round"
                        transform="rotate(-90 55 55)"
                        strokeDasharray={`${(Math.max(0, Math.min(100, health.score)) / 100) * 264} 264`}
                      />
                      <text
                        x="55"
                        y="62"
                        textAnchor="middle"
                        fontSize="24"
                        fontWeight="700"
                        fill="#111827"
                      >
                        {Math.round(health.score)}
                      </text>
                    </svg>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
                      <span>频率扣分：{health.components.freq.toFixed(1)}</span>
                      <span>SLA 扣分：{health.components.sla.toFixed(1)}</span>
                      <span>CSAT 扣分：{health.components.csat.toFixed(1)}</span>
                      <span>情绪扣分：{health.components.sentiment.toFixed(1)}</span>
                    </div>
                  </div>
                  {health.trend.length > 1 ? (
                    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 46, marginTop: 12 }}>
                      {health.trend.slice(-30).map((p) => (
                        <div
                          key={p.date}
                          title={`${p.date}：${p.score}`}
                          style={{
                            flex: 1,
                            height: `${Math.max(4, Math.min(100, p.score))}%`,
                            background: p.score >= 80 ? '#86efac' : p.score >= 60 ? '#fcd34d' : '#fca5a5',
                            borderRadius: 2
                          }}
                        />
                      ))}
                    </div>
                  ) : null}
                </>
              )}
            </div>
          </div>

          {/* V2.3 M5：客户情绪趋势 */}
          <div className="card">
            <div className="panel-head">
              <span className="panel-title">情绪趋势（90 天）</span>
            </div>
            <div className="panel-body">
              {sentimentTrend.length === 0 ? (
                <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无情绪数据</div>
              ) : (
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 56 }}>
                  {sentimentTrend.slice(-30).map((p) => (
                    <div
                      key={p.date}
                      title={`${p.date}：负面占比 ${(p.negativeRatio * 100).toFixed(0)}%`}
                      style={{
                        flex: 1,
                        height: `${Math.max(4, p.negativeRatio * 100)}%`,
                        background: '#fca5a5',
                        borderRadius: 2
                      }}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* 联系人卡片 */}
          <div className="card">
            <div className="panel-head">
              <span className="panel-title">联系人</span>
              <button className="btn sm" style={{ marginLeft: 'auto' }} onClick={openCreateContact}>
                + 新增
              </button>
            </div>
            <div className="panel-body">
              {contacts.length === 0 ? (
                <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>暂无联系人，点击「新增」添加</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {contacts.map((c) => (
                    <div
                      key={c.id}
                      style={{
                        border: '1px solid var(--line)',
                        borderRadius: 8,
                        padding: 10,
                        background: c.isPrimary ? '#f0fdf4' : '#fff'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <b style={{ fontSize: 13 }}>{c.name}</b>
                        {c.isPrimary ? (
                          <span className="pill st-res">主联系人</span>
                        ) : (
                          <button
                            className="btn ghost sm"
                            style={{ padding: '0 6px', height: 20, fontSize: 10 }}
                            onClick={() => doSetPrimary(c)}
                          >
                            设为主
                          </button>
                        )}
                        <button
                          className="btn ghost sm"
                          style={{ padding: '0 6px', height: 20, fontSize: 10, marginLeft: 'auto' }}
                          onClick={() => openEditContact(c)}
                        >
                          编辑
                        </button>
                        <button
                          className="btn danger sm"
                          style={{ padding: '0 6px', height: 20, fontSize: 10 }}
                          onClick={() => doDeleteContact(c)}
                        >
                          删除
                        </button>
                      </div>
                      <div className="kv" style={{ padding: '4px 0', fontSize: 11.5 }}>
                        <span className="k">邮箱</span>
                        <span className="v">{c.email ? maskEmail(c.email) : '—'}</span>
                      </div>
                      <div className="kv" style={{ padding: '4px 0', fontSize: 11.5 }}>
                        <span className="k">电话</span>
                        <span className="v">{c.phone ? maskPhone(c.phone) : '—'}</span>
                      </div>
                      {c.title ? (
                        <div className="kv" style={{ padding: '4px 0', fontSize: 11.5 }}>
                          <span className="k">职位</span>
                          <span className="v">{c.title}</span>
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 联系人新增 / 编辑弹窗 */}
      <Modal
        open={contactOpen}
        onClose={() => setContactOpen(false)}
        title={editingContact ? '编辑联系人' : '新增联系人'}
        subtitle={`客户：${customer.name}`}
        icon={editingContact ? '✎' : '+'}
        width={440}
        footer={
          <>
            <span className="note">设为主联系人后，该客户原有主联系人将自动取消</span>
            <button className="btn ghost sm" onClick={() => setContactOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmContact} disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="field">
          <label>
            姓名 <span className="req">*</span>
          </label>
          <input
            className="input"
            autoFocus
            value={cName}
            onChange={(e) => setCName(e.target.value)}
            placeholder="联系人姓名"
          />
        </div>
        <div className="field">
          <label>邮箱</label>
          <input
            className="input"
            value={cEmail}
            onChange={(e) => setCEmail(e.target.value)}
            placeholder="同客户内唯一"
          />
        </div>
        <div className="field">
          <label>电话</label>
          <input className="input" value={cPhone} onChange={(e) => setCPhone(e.target.value)} />
        </div>
        <div className="field">
          <label>职位</label>
          <input className="input" value={cTitle} onChange={(e) => setCTitle(e.target.value)} />
        </div>
        <label
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            fontSize: 12,
            color: 'var(--ink-2)',
            marginTop: 4
          }}
        >
          <input
            type="checkbox"
            checked={cPrimary}
            onChange={(e) => setCPrimary(e.target.checked)}
            style={{ accentColor: '#2563eb' }}
          />
          设为该客户的主联系人
        </label>
      </Modal>
    </>
  );
}
