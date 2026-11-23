import { useEffect, useState } from 'react';
import { api, getToken, setAuth } from '@/api/client';
import { useToast } from '@/components/Toast';
import type { UserInfo } from '@/types';

const ROLE_LABEL: Record<string, string> = {
  ADMIN: '管理员',
  MANAGER: '主管',
  ENGINEER: '支持工程师',
  KB_ADMIN: '知识库管理员'
};

const SCOPE_LABEL: Record<string, string> = {
  SELF: '仅本人',
  TEAM: '本组',
  ALL: '全部'
};

export default function Profile() {
  const { showToast } = useToast();
  const [me, setMe] = useState<UserInfo | null>(null);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [pwd, setPwd] = useState('');
  const [pwd2, setPwd2] = useState('');
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const u = await api.getMe();
      setMe(u);
      setName(u.name || '');
      setEmail(u.email || '');
    } catch (e) {
      showToast('加载个人信息失败：' + (e as Error).message);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = async () => {
    if (!name.trim()) {
      showToast('姓名不能为空');
      return;
    }
    if (pwd && pwd !== pwd2) {
      showToast('两次输入的密码不一致');
      return;
    }
    if (pwd && pwd.length < 6) {
      showToast('密码长度至少 6 位');
      return;
    }
    setSaving(true);
    try {
      const updated = await api.updateMe({ name: name.trim(), email: email.trim(), password: pwd || undefined });
      setMe(updated);
      setPwd('');
      setPwd2('');
      // 同步本地缓存，侧边栏姓名即时更新
      setAuth(getToken(), updated);
      showToast('个人资料已保存');
    } catch (e) {
      showToast('保存失败：' + (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div className="page-title">个人设置</div>
          <div className="page-sub">修改显示名称与登录密码</div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <div className="card" style={{ flex: '1 1 380px', padding: 18 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 14 }}>基本资料</div>
          <div className="field">
            <label>用户名</label>
            <input className="input" value={me?.username || ''} disabled />
          </div>
          <div className="field">
            <label>
              显示名称 <span className="req">*</span>
            </label>
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="显示名称" />
          </div>
          <div className="field">
            <label>
              通知邮箱 <span className="note">（留空则用「登录名@mail-domain」）</span>
            </label>
            <input
              className="input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@company.com"
            />
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>
              新密码 <span className="note">（留空则不修改）</span>
            </label>
            <input
              className="input"
              type="password"
              value={pwd}
              onChange={(e) => setPwd(e.target.value)}
              placeholder="至少 6 位"
            />
          </div>
          <div className="field" style={{ marginTop: 12 }}>
            <label>确认新密码</label>
            <input
              className="input"
              type="password"
              value={pwd2}
              onChange={(e) => setPwd2(e.target.value)}
              placeholder="再次输入新密码"
            />
          </div>
          <div style={{ marginTop: 16 }}>
            <button className="btn primary sm" onClick={save} disabled={saving}>
              {saving ? '保存中…' : '保存修改'}
            </button>
          </div>
        </div>

        <div className="card" style={{ flex: '1 1 260px', padding: 18 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 14 }}>账号权限</div>
          <div style={{ display: 'grid', gridTemplateColumns: '80px 1fr', gap: '8px 12px', fontSize: 12.5 }}>
            <div style={{ color: 'var(--muted-2)' }}>角色</div>
            <div>{ROLE_LABEL[(me?.role || '').toUpperCase()] || me?.role || '—'}</div>
            <div style={{ color: 'var(--muted-2)' }}>工作组</div>
            <div>{me?.teamId ? `#${me.teamId}` : '未分配'}</div>
            <div style={{ color: 'var(--muted-2)' }}>数据范围</div>
            <div>{SCOPE_LABEL[(me?.dataScope || '').toUpperCase()] || me?.dataScope || '—'}</div>
          </div>
          <div className="note" style={{ marginTop: 14 }}>
            角色、工作组与数据范围由管理员在「用户管理」中调整。
          </div>
        </div>
      </div>
    </>
  );
}
