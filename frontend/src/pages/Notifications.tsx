import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api/client';
import { useToast } from '@/components/Toast';
import type { AppNotification } from '@/types';

const TABS = [
  { v: '', t: '全部' },
  { v: 'TICKET_ASSIGNED', t: '工单分派' },
  { v: 'TICKET_TRANSFERRED', t: '工单转派' },
  { v: 'SLA_WARNING', t: 'SLA 预警' },
  { v: 'SLA_BREACH', t: 'SLA 超时' }
];

const PAGE_SIZE = 20;

const typePill = (t: string) =>
  t === 'TICKET_ASSIGNED' ? 'p2' : t === 'TICKET_TRANSFERRED' ? 'p3' : t === 'SLA_BREACH' ? 'p1' : 'soft';

export default function Notifications() {
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [tab, setTab] = useState('');
  const [page, setPage] = useState(1);
  const [list, setList] = useState<AppNotification[]>([]);
  const [total, setTotal] = useState(0);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);

  const notifyChanged = () => window.dispatchEvent(new Event('sp:notify'));

  const loadUnread = () => {
    api
      .getUnreadCount()
      .then((r) => setUnread(r.count || 0))
      .catch(() => {});
  };

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.getNotifications(tab || undefined, page, PAGE_SIZE);
      setList(res.list);
      setTotal(res.total);
    } catch (e) {
      showToast('加载通知失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUnread();
  }, []);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, page]);

  const open = async (n: AppNotification) => {
    if (!n.isRead) {
      try {
        await api.markNotificationRead(n.id);
        setList((prev) => prev.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)));
        loadUnread();
        notifyChanged();
      } catch {
        // 标记失败不阻断跳转
      }
    }
    if (n.refType === 'ticket' && n.refId) {
      navigate('/tickets/' + n.refId);
    }
  };

  const readAll = async () => {
    try {
      const r = await api.markAllNotificationsRead();
      showToast(r.updated > 0 ? `已将 ${r.updated} 条通知标记为已读` : '没有未读通知');
      setList((prev) => prev.map((x) => ({ ...x, isRead: true })));
      loadUnread();
      notifyChanged();
    } catch (e) {
      showToast('操作失败：' + (e as Error).message);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">通知中心</div>
          <div className="page-sub">
            共 {total} 条 · 未读 <b>{unread}</b> 条
          </div>
        </div>
        <div className="spacer"></div>
        <button className="btn ghost sm" onClick={readAll} disabled={unread === 0}>
          全部标为已读
        </button>
      </div>

      <div className="tabs" style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
        {TABS.map((t) => (
          <button
            key={t.v}
            className={'btn sm ' + (tab === t.v ? 'primary' : 'ghost')}
            onClick={() => {
              setTab(t.v);
              setPage(1);
            }}
          >
            {t.t}
          </button>
        ))}
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {loading ? (
          <div className="empty" style={{ padding: 48 }}>
            加载中…
          </div>
        ) : list.length === 0 ? (
          <div className="empty" style={{ padding: 48 }}>
            暂无通知
          </div>
        ) : (
          list.map((n) => (
            <div
              key={n.id}
              className={'notify-item' + (n.isRead ? '' : ' unread')}
              onClick={() => open(n)}
            >
              <span className={'notify-dot' + (n.isRead ? ' read' : '')}></span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <span className={'pill ' + typePill(n.type)}>{n.typeLabel || n.type}</span>
                  <span className="t-title" style={{ fontWeight: n.isRead ? 500 : 700 }}>
                    {n.title}
                  </span>
                </div>
                {n.content && (
                  <div style={{ color: 'var(--ink-2)', fontSize: 12.5, marginTop: 4 }}>{n.content}</div>
                )}
                <div style={{ color: 'var(--muted-2)', fontSize: 11.5, marginTop: 5 }}>{n.createdAt}</div>
              </div>
            </div>
          ))
        )}
      </div>

      {total > PAGE_SIZE && (
        <div className="list-foot">
          <span style={{ color: 'var(--muted-2)', fontSize: 12 }}>
            第 {page} / {totalPages} 页
          </span>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
            <button className="btn ghost sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
              上一页
            </button>
            <button className="btn ghost sm" disabled={page >= totalPages} onClick={() => setPage(page + 1)}>
              下一页
            </button>
          </div>
        </div>
      )}
    </>
  );
}
