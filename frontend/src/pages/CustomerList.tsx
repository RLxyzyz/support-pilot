import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { Customer } from '@/types';

const SLA_TIERS = ['全部', '金牌', '银牌', '铜牌', '默认'];
const SLA_OPTIONS = ['金牌', '银牌', '铜牌', '默认'];
const STATUS_OPTIONS = [
  { v: 'active', t: '启用' },
  { v: 'disabled', t: '停用' }
];
const PAGE_SIZES = [10, 20, 50];

const slaPill = (t: string) => (t === '金牌' ? 'p2' : t === '银牌' ? 'p3' : t === '铜牌' ? 'p4' : 'soft');

export default function CustomerList() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [list, setList] = useState<Customer[]>([]);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [slaTier, setSlaTier] = useState('全部');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(true);

  // 新建 / 编辑弹窗
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [fName, setFName] = useState('');
  const [fCode, setFCode] = useState('');
  const [fSla, setFSla] = useState('默认');
  const [fContractNo, setFContractNo] = useState('');
  const [fStart, setFStart] = useState('');
  const [fEnd, setFEnd] = useState('');
  const [fStatus, setFStatus] = useState('active');
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const data = await api.getCustomers({
        keyword: keyword.trim(),
        slaTier: slaTier === '全部' ? '' : slaTier,
        page,
        size
      });
      setList(data.list);
      setTotal(data.total);
    } catch (e) {
      showToast('加载客户失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, slaTier, page, size]);

  const totalPages = Math.max(Math.ceil(total / size), 1);
  const pageNumbers = (): (number | '...')[] => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
    const pages: (number | '...')[] = [1];
    const start = Math.max(2, page - 1);
    const end = Math.min(totalPages - 1, page + 1);
    if (start > 2) pages.push('...');
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < totalPages - 1) pages.push('...');
    pages.push(totalPages);
    return pages;
  };

  const openCreate = () => {
    setEditing(null);
    setFName('');
    setFCode('');
    setFSla('默认');
    setFContractNo('');
    setFStart('');
    setFEnd('');
    setFStatus('active');
    setOpen(true);
  };

  const openEdit = (c: Customer) => {
    setEditing(c);
    setFName(c.name);
    setFCode(c.code);
    setFSla(c.slaTier || '默认');
    setFContractNo(c.contractNo || '');
    setFStart(c.contractStart || '');
    setFEnd(c.contractEnd || '');
    setFStatus(c.status || 'active');
    setOpen(true);
  };

  const confirmSave = async () => {
    if (!fName.trim() || !fCode.trim()) {
      showToast('请填写客户名称与编码');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: fName.trim(),
        code: fCode.trim(),
        slaTier: fSla,
        contractNo: fContractNo.trim() || undefined,
        contractStart: fStart || undefined,
        contractEnd: fEnd || undefined,
        status: fStatus
      };
      if (editing) {
        await api.updateCustomer(editing.id, payload);
        showToast('客户已更新');
      } else {
        await api.createCustomer(payload);
        showToast('客户已创建');
      }
      setOpen(false);
      load();
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const doDisable = async (c: Customer) => {
    try {
      await api.disableCustomer(c.id);
      showToast(`已停用客户「${c.name}」`);
      load();
    } catch (e) {
      showToast('停用失败：' + (e as Error).message);
    }
  };

  const doDelete = async (c: Customer) => {
    if (!window.confirm(`确认删除客户「${c.name}」？删除后不可恢复。\n若该客户存在关联工单，将无法删除。`)) return;
    try {
      await api.deleteCustomer(c.id);
      showToast('客户已删除');
      if (list.length === 1 && page > 1) setPage(page - 1);
      else load();
    } catch (e) {
      showToast('删除失败：' + (e as Error).message);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">客户</div>
          <div className="page-sub">共 {total.toLocaleString()} 个客户 · 管理客户档案、合同与联系人</div>
        </div>
        <div className="spacer"></div>
        <button className="btn primary" onClick={openCreate}>
          + 新建客户
        </button>
      </div>

      <div className="filterbar">
        <div className="chips">
          {SLA_TIERS.map((t) => (
            <div
              key={t}
              className={'chip' + (slaTier === t ? ' active' : '')}
              onClick={() => {
                setSlaTier(t);
                setPage(1);
              }}
            >
              {t}
            </div>
          ))}
        </div>
        <div className="spacer"></div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input
            className="input"
            placeholder="搜索客户名称 / 编码…"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setKeyword(keywordInput.trim());
                setPage(1);
              }
            }}
            style={{ width: 220, height: 30 }}
          />
          <button
            className="btn sm"
            onClick={() => {
              setKeyword(keywordInput.trim());
              setPage(1);
            }}
          >
            搜索
          </button>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>客户名称</th>
              <th style={{ width: 140 }}>客户编码</th>
              <th style={{ width: 90 }}>服务等级</th>
              <th style={{ width: 80 }}>状态</th>
              <th style={{ width: 90 }}>关联工单</th>
              <th style={{ width: 110 }}>合同到期</th>
              <th style={{ width: 180 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7}>
                  <div className="empty">加载中…</div>
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={7}>
                  <div className="empty">暂无客户，点击右上角「新建客户」开始</div>
                </td>
              </tr>
            ) : (
              list.map((c) => (
                <tr key={c.id} onClick={() => navigate('/customers/' + c.id)}>
                  <td>
                    <span className="t-title">{c.name}</span>
                    {c.contractNo ? <span className="t-sub">合同：{c.contractNo}</span> : null}
                  </td>
                  <td style={{ color: 'var(--ink-2)' }}>{c.code}</td>
                  <td>
                    <span className={'pill ' + slaPill(c.slaTier)}>{c.slaTier}</span>
                  </td>
                  <td>
                    <span className={'pill ' + (c.status === 'active' ? 'st-res' : 'soft')}>
                      {c.status === 'active' ? '启用' : '停用'}
                    </span>
                  </td>
                  <td style={{ color: 'var(--ink-2)' }}>{c.ticketCount}</td>
                  <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>{c.contractEnd || '—'}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button className="btn ghost sm" onClick={() => navigate('/customers/' + c.id)}>
                        详情
                      </button>
                      <button className="btn ghost sm" onClick={() => openEdit(c)}>
                        编辑
                      </button>
                      {c.status === 'active' ? (
                        <button className="btn ghost sm" onClick={() => doDisable(c)}>
                          停用
                        </button>
                      ) : null}
                      <button className="btn danger sm" onClick={() => doDelete(c)}>
                        删除
                      </button>
                    </div>
                  </td>
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
          {pageNumbers().map((p, idx) =>
            p === '...' ? (
              <span key={`e-${idx}`} style={{ padding: '0 4px', color: 'var(--muted-2)', alignSelf: 'center' }}>
                …
              </span>
            ) : (
              <button key={p} className={'pg' + (p === page ? ' active' : '')} onClick={() => setPage(p)}>
                {p}
              </button>
            )
          )}
          <button className="pg" disabled={page >= totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>
            ›
          </button>
        </div>
      </div>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? '编辑客户' : '新建客户'}
        subtitle={editing ? `当前客户：${editing.name}` : '创建一条客户档案，关联工单与联系人'}
        icon={editing ? '✎' : '+'}
        footer={
          <>
            <span className="note">客户编码在租户内唯一，创建后建议不再变更</span>
            <button className="btn ghost sm" onClick={() => setOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmSave} disabled={saving}>
              {saving ? '保存中…' : editing ? '保存修改' : '创建客户'}
            </button>
          </>
        }
      >
        <div className="field">
          <label>
            客户名称 <span className="req">*</span>
          </label>
          <input
            className="input"
            autoFocus
            value={fName}
            onChange={(e) => setFName(e.target.value)}
            placeholder="例如：字节跳动"
          />
        </div>
        <div className="field">
          <label>
            客户编码 <span className="req">*</span>
          </label>
          <input
            className="input"
            value={fCode}
            onChange={(e) => setFCode(e.target.value)}
            placeholder="租户内唯一，例如：BD-001"
          />
        </div>
        <div className="field">
          <label>服务等级</label>
          <select className="select" value={fSla} onChange={(e) => setFSla(e.target.value)}>
            {SLA_OPTIONS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>合同编号</label>
          <input
            className="input"
            value={fContractNo}
            onChange={(e) => setFContractNo(e.target.value)}
            placeholder="选填"
          />
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <div className="field" style={{ flex: 1 }}>
            <label>合同开始</label>
            <input type="date" className="input" value={fStart} onChange={(e) => setFStart(e.target.value)} />
          </div>
          <div className="field" style={{ flex: 1 }}>
            <label>合同到期</label>
            <input type="date" className="input" value={fEnd} onChange={(e) => setFEnd(e.target.value)} />
          </div>
        </div>
        <div className="field">
          <label>状态</label>
          <select className="select" value={fStatus} onChange={(e) => setFStatus(e.target.value)}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.v} value={o.v}>
                {o.t}
              </option>
            ))}
          </select>
        </div>
      </Modal>
    </>
  );
}
