import { useEffect, useState } from 'react';
import { api, isAdmin } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { OperationLog } from '@/types';

const MODULE_OPTIONS = [
  { v: '', t: '全部模块' },
  { v: 'auth', t: '认证' },
  { v: 'ticket', t: '工单' },
  { v: 'kb', t: '知识库' },
  { v: 'user', t: '用户' },
  { v: 'draft', t: '草稿' },
  { v: 'agent', t: '智能体' }
];

const ACTION_OPTIONS = [
  { v: '', t: '全部动作' },
  { v: 'login', t: '登录' },
  { v: 'register', t: '注册' },
  { v: 'create', t: '创建' },
  { v: 'update', t: '更新' },
  { v: 'delete', t: '删除' },
  { v: 'assign', t: '转派' },
  { v: 'accept', t: '接单' },
  { v: 'escalate', t: '升级' },
  { v: 'reply', t: '回复' },
  { v: 'import', t: '导入' },
  { v: 'approve', t: '采纳' },
  { v: 'reject', t: '驳回' },
  { v: 'reindex', t: '重建索引' },
  { v: 'sync', t: '同步' }
];

const MODULE_LABEL: Record<string, string> = Object.fromEntries(MODULE_OPTIONS.filter((o) => o.v).map((o) => [o.v, o.t]));
const ACTION_LABEL: Record<string, string> = Object.fromEntries(ACTION_OPTIONS.filter((o) => o.v).map((o) => [o.v, o.t]));

const PAGE_SIZES = [10, 20, 50];

function fmtTime(s?: string): string {
  // 后端已统一返回 yyyy-MM-dd HH:mm:ss，直接展示
  return s || '-';
}

function prettyJson(s?: string): string {
  if (!s) return '';
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}

const toIso = (v: string) => (v ? new Date(v).toISOString() : '');

export default function OperationLogs() {
  const { showToast } = useToast();
  const admin = isAdmin();

  const [logs, setLogs] = useState<OperationLog[]>([]);
  const [total, setTotal] = useState(0);
  const [module, setModule] = useState('');
  const [action, setAction] = useState('');
  const [operator, setOperator] = useState('');
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [forbidden, setForbidden] = useState(false);

  // 详情弹窗
  const [detail, setDetail] = useState<OperationLog | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const load = async () => {
    if (!admin) return;
    setLoading(true);
    setForbidden(false);
    try {
      const params: Record<string, string> = {};
      if (module) params.module = module;
      if (action) params.action = action;
      if (operator.trim()) params.operator = operator.trim();
      if (start) params.start = toIso(start);
      if (end) params.end = toIso(end);
      params.page = String(page);
      params.size = String(size);
      const data = await api.getOperationLogs(params);
      setLogs(data.list);
      setTotal(data.total);
    } catch (e) {
      const msg = (e as Error).message;
      if (msg.includes('管理员') || msg.includes('403')) {
        setForbidden(true);
      }
      showToast('加载日志失败：' + msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [module, action, page, size]);

  const onSearch = () => {
    setPage(1);
    load();
  };

  const openDetail = async (log: OperationLog) => {
    setDetail(log);
    setDetailLoading(true);
    try {
      const full = await api.getOperationLog(log.id);
      setDetail(full);
    } catch (e) {
      showToast('加载详情失败：' + (e as Error).message);
    } finally {
      setDetailLoading(false);
    }
  };

  const totalPages = Math.max(Math.ceil(total / size), 1);

  if (!admin) {
    return (
      <div className="empty" style={{ padding: 60, textAlign: 'center' }}>
        需要管理员权限才能查看操作日志
      </div>
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">操作日志</div>
          <div className="page-sub">记录各模块的关键操作、操作人、操作内容与前后快照，共 {total.toLocaleString()} 条</div>
        </div>
      </div>

      <div className="filterbar">
        <select
          value={module}
          onChange={(e) => {
            setModule(e.target.value);
            setPage(1);
          }}
          className="select"
          style={{ width: 'auto', height: 30, padding: '0 8px' }}
        >
          {MODULE_OPTIONS.map((o) => (
            <option key={o.v} value={o.v}>
              {o.t}
            </option>
          ))}
        </select>
        <select
          value={action}
          onChange={(e) => {
            setAction(e.target.value);
            setPage(1);
          }}
          className="select"
          style={{ width: 'auto', height: 30, padding: '0 8px' }}
        >
          {ACTION_OPTIONS.map((o) => (
            <option key={o.v} value={o.v}>
              {o.t}
            </option>
          ))}
        </select>
        <input
          className="input"
          placeholder="操作人姓名…"
          value={operator}
          onChange={(e) => setOperator(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && onSearch()}
          style={{ width: 140, height: 30 }}
        />
        <input
          type="datetime-local"
          className="input"
          value={start}
          onChange={(e) => setStart(e.target.value)}
          style={{ width: 'auto', height: 30 }}
        />
        <span style={{ color: 'var(--muted-2)' }}>至</span>
        <input
          type="datetime-local"
          className="input"
          value={end}
          onChange={(e) => setEnd(e.target.value)}
          style={{ width: 'auto', height: 30 }}
        />
        <button className="btn sm" onClick={onSearch}>
          查询
        </button>
        <div className="spacer"></div>
      </div>

      {forbidden ? (
        <div className="empty" style={{ padding: 40 }}>无权限查看操作日志</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th style={{ width: 150 }}>时间</th>
                <th style={{ width: 90 }}>操作人</th>
                <th style={{ width: 70 }}>模块</th>
                <th style={{ width: 70 }}>动作</th>
                <th style={{ width: 110 }}>对象</th>
                <th>摘要</th>
                <th style={{ width: 70 }}>结果</th>
                <th style={{ width: 110 }}>IP</th>
                <th style={{ width: 70 }}>耗时</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={9}>
                    <div className="empty">加载中…</div>
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={9}>
                    <div className="empty">暂无日志</div>
                  </td>
                </tr>
              ) : (
                logs.map((l) => (
                  <tr key={l.id} onClick={() => openDetail(l)} style={{ cursor: 'pointer' }}>
                    <td style={{ color: 'var(--muted-2)', fontSize: 11.5, whiteSpace: 'nowrap' }}>{fmtTime(l.createdAt)}</td>
                    <td>{l.operatorName || l.operatorUsername || (l.result === 'fail' ? '未知' : '系统')}</td>
                    <td>{MODULE_LABEL[l.module] || l.module}</td>
                    <td>{ACTION_LABEL[l.action] || l.action}</td>
                    <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>
                      {l.targetType ? `${l.targetType}${l.targetId ? ' #' + l.targetId : ''}` : '-'}
                    </td>
                    <td>
                      <span className="t-title">{l.summary || '-'}</span>
                      {l.result === 'fail' && l.errorMsg ? <span className="t-sub" style={{ color: '#c0392b' }}>{l.errorMsg}</span> : null}
                    </td>
                    <td>
                      <span className={'pill ' + (l.result === 'success' ? 'p4' : 'p1')}>
                        {l.result === 'success' ? '成功' : '失败'}
                      </span>
                    </td>
                    <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>{l.ip || '-'}</td>
                    <td style={{ color: 'var(--muted-2)', fontSize: 11.5 }}>{l.costMs != null ? l.costMs + ' ms' : '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

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
          <button className="pg" disabled={page >= totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>
            ›
          </button>
        </div>
      </div>

      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title="操作详情"
        subtitle={detail ? `${MODULE_LABEL[detail.module] || detail.module} / ${ACTION_LABEL[detail.action] || detail.action}` : ''}
        icon="i"
        footer={
          <button className="btn sm" onClick={() => setDetail(null)}>
            关闭
          </button>
        }
      >
        {detail ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '90px 1fr', gap: '6px 12px', fontSize: 12.5 }}>
              <div style={{ color: 'var(--muted-2)' }}>时间</div>
              <div>{fmtTime(detail.createdAt)}</div>
              <div style={{ color: 'var(--muted-2)' }}>操作人</div>
              <div>{detail.operatorName || detail.operatorUsername || '-'}（id={detail.operatorId ?? '-'}）</div>
              <div style={{ color: 'var(--muted-2)' }}>对象</div>
              <div>{detail.targetType ? `${detail.targetType} #${detail.targetId || '-'}` : '-'}</div>
              <div style={{ color: 'var(--muted-2)' }}>结果</div>
              <div>{detail.result === 'success' ? '成功' : '失败'}{detail.costMs != null ? ` · ${detail.costMs} ms` : ''}</div>
              <div style={{ color: 'var(--muted-2)' }}>IP / UA</div>
              <div style={{ wordBreak: 'break-all' }}>{detail.ip || '-'} · {detail.userAgent || '-'}</div>
              {detail.result === 'fail' && detail.errorMsg ? (
                <>
                  <div style={{ color: 'var(--muted-2)' }}>错误</div>
                  <div style={{ color: '#c0392b', wordBreak: 'break-all' }}>{detail.errorMsg}</div>
                </>
              ) : null}
            </div>

            <JsonBlock label="请求参数（password 已脱敏）" value={detail.requestParams} loading={detailLoading} />
            <JsonBlock label="操作前快照" value={detail.beforeData} loading={detailLoading} />
            <JsonBlock label="操作后快照" value={detail.afterData} loading={detailLoading} />
          </div>
        ) : null}
      </Modal>
    </>
  );
}

function JsonBlock({ label, value, loading }: { label: string; value?: string; loading: boolean }) {
  const pretty = prettyJson(value);
  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--muted-2)', marginBottom: 4 }}>{label}</div>
      {loading ? (
        <div className="empty" style={{ padding: 12 }}>加载中…</div>
      ) : pretty ? (
        <pre
          style={{
            margin: 0,
            padding: 10,
            background: '#0f172a',
            color: '#e2e8f0',
            borderRadius: 6,
            fontSize: 11.5,
            lineHeight: 1.5,
            maxHeight: 220,
            overflow: 'auto',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all'
          }}
        >
          {pretty}
        </pre>
      ) : (
        <div style={{ fontSize: 12, color: 'var(--muted-2)', padding: '4px 0' }}>无</div>
      )}
    </div>
  );
}
