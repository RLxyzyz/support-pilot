import { useState, type FormEvent, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setAuth } from '@/api/client';

/**
 * 注册页：调用 /api/auth/register，成功后保存 token 直接登录态进入首页（免二次登录）。
 * 自助注册默认创建 support 角色、绑定默认租户，不允许通过此接口注册 admin。
 */
export default function Register() {
  const [name, setName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!name.trim()) {
      setError('请输入姓名');
      return;
    }
    if (username.trim().length < 3) {
      setError('用户名至少 3 个字符');
      return;
    }
    if (password.length < 6) {
      setError('密码至少 6 个字符');
      return;
    }
    if (password !== confirm) {
      setError('两次输入的密码不一致');
      return;
    }
    setLoading(true);
    try {
      const res = await api.register(username.trim(), password, name.trim());
      setAuth(res.token, res.user);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={wrap}>
      <div style={card}>
        <div style={brand}>
          <div style={mark}>S</div>
          <div>
            <div style={{ fontSize: 18, fontWeight: 700, color: '#0f172a' }}>注册账号</div>
            <div style={{ fontSize: 12, color: '#94a3b8' }}>SupportPilot · 技术支持工单系统</div>
          </div>
        </div>
        <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={label}>姓名</label>
            <input
              style={input}
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
              placeholder="你的显示名"
              autoComplete="name"
            />
          </div>
          <div>
            <label style={label}>用户名</label>
            <input
              style={input}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="3-32 个字符"
              autoComplete="username"
            />
          </div>
          <div>
            <label style={label}>密码</label>
            <input
              style={input}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="至少 6 个字符"
              autoComplete="new-password"
            />
          </div>
          <div>
            <label style={label}>确认密码</label>
            <input
              style={input}
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              placeholder="再次输入密码"
              autoComplete="new-password"
            />
          </div>
          {error && <div style={{ color: '#dc2626', fontSize: 12 }}>{error}</div>}
          <button style={{ ...btn, opacity: loading ? 0.7 : 1 }} disabled={loading} type="submit">
            {loading ? '注册中…' : '注 册'}
          </button>
        </form>
        <div style={{ marginTop: 16, fontSize: 12, color: '#94a3b8', textAlign: 'center' }}>
          已有账号？
          <Link to="/login" style={{ color: '#2563eb', textDecoration: 'none', marginLeft: 4 }}>
            去登录
          </Link>
        </div>
      </div>
    </div>
  );
}

const wrap: CSSProperties = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: 'linear-gradient(135deg,#eff6ff 0%,#f5f7fb 60%)'
};
const card: CSSProperties = {
  width: 360,
  padding: '32px 28px',
  background: '#fff',
  borderRadius: 12,
  boxShadow: '0 8px 30px rgba(15,23,42,0.08)',
  border: '1px solid #e6ebf3'
};
const brand: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 };
const mark: CSSProperties = {
  width: 34,
  height: 34,
  borderRadius: 8,
  background: '#2563eb',
  color: '#fff',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  fontWeight: 700,
  fontSize: 18
};
const label: CSSProperties = { display: 'block', fontSize: 12, color: '#64748b', marginBottom: 6 };
const input: CSSProperties = {
  width: '100%',
  padding: '9px 11px',
  border: '1px solid #d5dde9',
  borderRadius: 8,
  fontSize: 13.5,
  outline: 'none',
  boxSizing: 'border-box'
};
const btn: CSSProperties = {
  marginTop: 4,
  padding: '10px',
  border: 'none',
  borderRadius: 8,
  background: '#2563eb',
  color: '#fff',
  fontSize: 14,
  fontWeight: 600,
  cursor: 'pointer'
};
