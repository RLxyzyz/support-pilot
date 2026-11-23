import { useEffect, useState } from 'react';
import { api, isAdmin } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { MailTask, NotifyRule } from '@/types';

export default function NotifyRules() {
  const { showToast } = useToast();
  const admin = isAdmin();

  const [rules, setRules] = useState<NotifyRule[]>([]);
  const [tasks, setTasks] = useState<MailTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [taskLoading, setTaskLoading] = useState(true);

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<NotifyRule | null>(null);
  const [fName, setFName] = useState('');
  const [fReceivers, setFReceivers] = useState('');
  const [fInApp, setFInApp] = useState(true);
  const [fEmail, setFEmail] = useState(false);
  const [fEnabled, setFEnabled] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadRules = async () => {
    setLoading(true);
    try {
      setRules(await api.getNotifyRules());
    } catch (e) {
      showToast('加载通知规则失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const loadTasks = async () => {
    setTaskLoading(true);
    try {
      setTasks(await api.listMailTasks('failed'));
    } catch (e) {
      showToast('加载邮件任务失败：' + (e as Error).message);
    } finally {
      setTaskLoading(false);
    }
  };

  useEffect(() => {
    if (!admin) return;
    loadRules();
    loadTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggle = async (rule: NotifyRule, patch: Partial<NotifyRule>) => {
    try {
      const updated = await api.updateNotifyRule(rule.id, {
        eventType: rule.eventType,
        name: rule.name,
        receivers: rule.receivers,
        inAppEnabled: rule.inAppEnabled,
        emailEnabled: rule.emailEnabled,
        enabled: rule.enabled,
        ...patch
      });
      setRules((prev) => prev.map((r) => (r.id === rule.id ? updated : r)));
      showToast('规则已更新');
    } catch (e) {
      showToast('更新失败：' + (e as Error).message);
      loadRules();
    }
  };

  const openEdit = (rule: NotifyRule) => {
    setEditing(rule);
    setFName(rule.name || '');
    setFReceivers(rule.receivers || '');
    setFInApp(rule.inAppEnabled);
    setFEmail(rule.emailEnabled);
    setFEnabled(rule.enabled);
    setOpen(true);
  };

  const save = async () => {
    if (!editing) return;
    if (!fReceivers.trim()) {
      showToast('接收人不能为空');
      return;
    }
    setSaving(true);
    try {
      const updated = await api.updateNotifyRule(editing.id, {
        eventType: editing.eventType,
        name: fName.trim(),
        receivers: fReceivers.trim(),
        inAppEnabled: fInApp,
        emailEnabled: fEmail,
        enabled: fEnabled
      });
      setRules((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      showToast('规则已保存');
      setOpen(false);
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const resend = async (t: MailTask) => {
    try {
      const vo = await api.resendMailTask(t.id);
      showToast(vo.status === 'sent' ? '邮件已发送' : '重发未成功：' + (vo.lastError || 'SMTP 不可用'));
      loadTasks();
    } catch (e) {
      showToast('重发失败：' + (e as Error).message);
    }
  };

  if (!admin) {
    return (
      <div className="empty" style={{ padding: 60, textAlign: 'center' }}>
        需要管理员权限才能配置通知规则
      </div>
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">通知规则</div>
          <div className="page-sub">配置事件接收人与通道开关（站内信 / 邮件），保存后即时生效</div>
        </div>
      </div>

      <div className="table-wrap" style={{ marginBottom: 18 }}>
        <table>
          <thead>
            <tr>
              <th style={{ width: 150 }}>事件</th>
              <th>名称</th>
              <th style={{ width: 200 }}>接收人</th>
              <th style={{ width: 80 }}>站内信</th>
              <th style={{ width: 80 }}>邮件</th>
              <th style={{ width: 80 }}>启用</th>
              <th style={{ width: 90 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7}>
                  <div className="empty">加载中…</div>
                </td>
              </tr>
            ) : (
              rules.map((r) => (
                <tr key={r.id}>
                  <td>
                    <span className="pill soft">{r.eventLabel || r.eventType}</span>
                  </td>
                  <td>{r.name || '—'}</td>
                  <td style={{ color: 'var(--ink-2)', fontSize: 12 }}>{r.receivers}</td>
                  <td>
                    <label className="chk">
                      <input
                        type="checkbox"
                        checked={r.inAppEnabled}
                        onChange={(e) => toggle(r, { inAppEnabled: e.target.checked })}
                      />
                    </label>
                  </td>
                  <td>
                    <label className="chk">
                      <input
                        type="checkbox"
                        checked={r.emailEnabled}
                        onChange={(e) => toggle(r, { emailEnabled: e.target.checked })}
                      />
                    </label>
                  </td>
                  <td>
                    <label className="chk">
                      <input
                        type="checkbox"
                        checked={r.enabled}
                        onChange={(e) => toggle(r, { enabled: e.target.checked })}
                      />
                    </label>
                  </td>
                  <td>
                    <button className="btn ghost sm" onClick={() => openEdit(r)}>
                      编辑
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="page-head" style={{ marginBottom: 10 }}>
        <div>
          <div className="page-title" style={{ fontSize: 15 }}>
            发送失败的邮件
          </div>
          <div className="page-sub">
            SMTP 未配置时邮件任务会直接标记失败，站内信不受影响；配置后可在此重发
          </div>
        </div>
        <div className="spacer"></div>
        <button className="btn ghost sm" onClick={loadTasks}>
          刷新
        </button>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th style={{ width: 200 }}>收件人</th>
              <th>主题</th>
              <th style={{ width: 70 }}>重试</th>
              <th style={{ width: 260 }}>失败原因</th>
              <th style={{ width: 90 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {taskLoading ? (
              <tr>
                <td colSpan={5}>
                  <div className="empty">加载中…</div>
                </td>
              </tr>
            ) : tasks.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  <div className="empty">暂无失败邮件</div>
                </td>
              </tr>
            ) : (
              tasks.map((t) => (
                <tr key={t.id}>
                  <td style={{ fontSize: 12.5 }}>{t.toAddr}</td>
                  <td>
                    <span className="t-title">{t.subject}</span>
                    <span className="t-sub">{t.updatedAt}</span>
                  </td>
                  <td style={{ color: 'var(--ink-2)' }}>{t.retryCount}</td>
                  <td style={{ color: 'var(--muted-2)', fontSize: 12 }}>{t.lastError || '—'}</td>
                  <td>
                    <button className="btn ghost sm" onClick={() => resend(t)}>
                      重发
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="编辑通知规则"
        subtitle={editing ? `事件：${editing.eventLabel || editing.eventType}` : ''}
        icon="✎"
        footer={
          <>
            <span className="note">接收人支持 ASSIGNEE / MANAGER / USER:12 / ROLE:ADMIN，逗号分隔</span>
            <button className="btn ghost sm" onClick={() => setOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={save} disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="field">
          <label>规则名称</label>
          <input className="input" value={fName} onChange={(e) => setFName(e.target.value)} placeholder="如：工单分派通知" />
        </div>
        <div className="field">
          <label>
            接收人 <span className="req">*</span>
          </label>
          <input
            className="input"
            value={fReceivers}
            onChange={(e) => setFReceivers(e.target.value)}
            placeholder="ASSIGNEE,MANAGER"
          />
        </div>
        <div className="field">
          <label>通道</label>
          <div style={{ display: 'flex', gap: 18, paddingTop: 4 }}>
            <label className="chk">
              <input type="checkbox" checked={fInApp} onChange={(e) => setFInApp(e.target.checked)} />
              站内信
            </label>
            <label className="chk">
              <input type="checkbox" checked={fEmail} onChange={(e) => setFEmail(e.target.checked)} />
              邮件
            </label>
            <label className="chk">
              <input type="checkbox" checked={fEnabled} onChange={(e) => setFEnabled(e.target.checked)} />
              启用该规则
            </label>
          </div>
        </div>
      </Modal>
    </>
  );
}
