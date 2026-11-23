import { useEffect, useState } from 'react';
import { api, isAdmin } from '@/api/client';
import { useToast } from '@/components/Toast';
import Modal from '@/components/Modal';
import type { Team, UserInfo } from '@/types';

const ROLES = [
  { v: 'ADMIN', t: '管理员' },
  { v: 'MANAGER', t: '主管' },
  { v: 'ENGINEER', t: '支持工程师' },
  { v: 'KB_ADMIN', t: '知识库管理员' }
];

const SCOPES = [
  { v: '', t: '默认（按角色）' },
  { v: 'SELF', t: '仅本人' },
  { v: 'TEAM', t: '本组' },
  { v: 'ALL', t: '全部' }
];

const ROLE_LABEL: Record<string, string> = Object.fromEntries(ROLES.map((r) => [r.v, r.t]));
const SCOPE_LABEL: Record<string, string> = Object.fromEntries(SCOPES.filter((s) => s.v).map((s) => [s.v, s.t]));

const rolePill = (r: string) => (r === 'ADMIN' ? 'p1' : r === 'MANAGER' ? 'p2' : r === 'KB_ADMIN' ? 'p3' : 'soft');

export default function UserManagement() {
  const { showToast } = useToast();
  const admin = isAdmin();

  const [users, setUsers] = useState<UserInfo[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [loading, setLoading] = useState(true);

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<UserInfo | null>(null);
  const [fUsername, setFUsername] = useState('');
  const [fName, setFName] = useState('');
  const [fEmail, setFEmail] = useState('');
  const [fPassword, setFPassword] = useState('');
  const [fRole, setFRole] = useState('ENGINEER');
  const [fTeamId, setFTeamId] = useState('');
  const [fScope, setFScope] = useState('');
  const [saving, setSaving] = useState(false);

  const load = async () => {
    if (!admin) return;
    setLoading(true);
    try {
      const [list, teamList] = await Promise.all([api.listUsers(), api.getTeams()]);
      setUsers(list);
      setTeams(teamList);
    } catch (e) {
      showToast('加载用户失败：' + (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const teamName = (id?: number) => teams.find((t) => t.id === id)?.name || (id ? `#${id}` : '—');

  const openCreate = () => {
    setEditing(null);
    setFUsername('');
    setFName('');
    setFEmail('');
    setFPassword('');
    setFRole('ENGINEER');
    setFTeamId('');
    setFScope('');
    setOpen(true);
  };

  const openEdit = (u: UserInfo) => {
    setEditing(u);
    setFUsername(u.username);
    setFName(u.name);
    setFEmail(u.email || '');
    setFPassword('');
    setFRole((u.role || 'ENGINEER').toUpperCase());
    setFTeamId(u.teamId ? String(u.teamId) : '');
    setFScope(u.dataScope || '');
    setOpen(true);
  };

  const confirmSave = async () => {
    if (!fName.trim()) {
      showToast('请填写姓名');
      return;
    }
    if (!editing && (!fUsername.trim() || !fPassword.trim())) {
      showToast('请填写用户名与初始密码');
      return;
    }
    setSaving(true);
    try {
      const base = {
        name: fName.trim(),
        email: fEmail.trim(),
        role: fRole,
        teamId: fTeamId ? Number(fTeamId) : undefined,
        dataScope: fScope || undefined
      };
      if (editing) {
        await api.updateUser(editing.id, { ...base, password: fPassword.trim() || undefined });
        showToast('用户已更新');
      } else {
        await api.createUser({ ...base, username: fUsername.trim(), password: fPassword.trim() });
        showToast('用户已创建');
      }
      setOpen(false);
      load();
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const doDelete = async (u: UserInfo) => {
    if (!window.confirm(`确认删除用户「${u.name}（${u.username}）」？删除后不可恢复。`)) return;
    try {
      await api.deleteUser(u.id);
      showToast('用户已删除');
      load();
    } catch (e) {
      showToast('删除失败：' + (e as Error).message);
    }
  };

  if (!admin) {
    return (
      <div className="empty" style={{ padding: 60, textAlign: 'center' }}>
        需要管理员权限才能管理用户
      </div>
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">用户管理</div>
          <div className="page-sub">共 {users.length} 个账号 · 分配角色、工作组与数据范围</div>
        </div>
        <div className="spacer"></div>
        <button className="btn primary" onClick={openCreate}>
          + 新建用户
        </button>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>姓名 / 用户名</th>
              <th style={{ width: 130 }}>角色</th>
              <th style={{ width: 160 }}>工作组</th>
              <th style={{ width: 110 }}>数据范围</th>
              <th style={{ width: 150 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={5}>
                  <div className="empty">加载中…</div>
                </td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  <div className="empty">暂无用户</div>
                </td>
              </tr>
            ) : (
              users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <span className="t-title">{u.name}</span>
                    <span className="t-sub">@{u.username}</span>
                    {u.email && <span className="t-sub">{u.email}</span>}
                  </td>
                  <td>
                    <span className={'pill ' + rolePill((u.role || '').toUpperCase())}>
                      {ROLE_LABEL[(u.role || '').toUpperCase()] || u.role}
                    </span>
                  </td>
                  <td style={{ color: 'var(--ink-2)' }}>{teamName(u.teamId)}</td>
                  <td style={{ color: 'var(--ink-2)' }}>
                    {SCOPE_LABEL[(u.dataScope || '').toUpperCase()] || u.dataScope || '—'}
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button className="btn ghost sm" onClick={() => openEdit(u)}>
                        编辑
                      </button>
                      <button className="btn danger sm" onClick={() => doDelete(u)}>
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

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? '编辑用户' : '新建用户'}
        subtitle={editing ? `当前用户：${editing.name}（@${editing.username}）` : '创建一个账号并分配角色与数据范围'}
        icon={editing ? '✎' : '+'}
        footer={
          <>
            <span className="note">数据范围「本组」需先分配工作组</span>
            <button className="btn ghost sm" onClick={() => setOpen(false)}>
              取消
            </button>
            <button className="btn primary sm" onClick={confirmSave} disabled={saving}>
              {saving ? '保存中…' : editing ? '保存修改' : '创建用户'}
            </button>
          </>
        }
      >
        <div className="field">
          <label>
            用户名 <span className="req">*</span>
          </label>
          <input
            className="input"
            value={fUsername}
            disabled={!!editing}
            onChange={(e) => setFUsername(e.target.value)}
            placeholder="登录账号，创建后不可修改"
          />
        </div>
        <div className="field">
          <label>
            姓名 <span className="req">*</span>
          </label>
          <input className="input" autoFocus value={fName} onChange={(e) => setFName(e.target.value)} placeholder="显示名称" />
        </div>
        <div className="field">
          <label>
            通知邮箱 <span className="note">（留空则用「登录名@mail-domain」）</span>
          </label>
          <input
            className="input"
            type="email"
            value={fEmail}
            onChange={(e) => setFEmail(e.target.value)}
            placeholder="name@company.com"
          />
        </div>
        <div className="field">
          <label>
            密码 {editing ? <span className="note">（留空则不修改）</span> : <span className="req">*</span>}
          </label>
          <input
            className="input"
            type="password"
            value={fPassword}
            onChange={(e) => setFPassword(e.target.value)}
            placeholder={editing ? '留空则保持原密码' : '初始登录密码'}
          />
        </div>
        <div className="field">
          <label>角色</label>
          <select className="select" value={fRole} onChange={(e) => setFRole(e.target.value)}>
            {ROLES.map((r) => (
              <option key={r.v} value={r.v}>
                {r.t}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>工作组</label>
          <select className="select" value={fTeamId} onChange={(e) => setFTeamId(e.target.value)}>
            <option value="">不分配</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}（{t.memberCount} 人）
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>数据范围</label>
          <select className="select" value={fScope} onChange={(e) => setFScope(e.target.value)}>
            {SCOPES.map((s) => (
              <option key={s.v} value={s.v}>
                {s.t}
              </option>
            ))}
          </select>
        </div>
      </Modal>
    </>
  );
}
