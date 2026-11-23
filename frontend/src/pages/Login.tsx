import { useState, type FormEvent, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setAuth } from '@/api/client';

/**
 * 登录页：调用 /api/auth/login，成功后保存 token 与用户信息，跳转首页。
 * 默认填入演示账号 admin / 123456，开箱即用。
 */
export default function Login() {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('123456');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const res = await api.login(username.trim(), password);
      setAuth(res.token, res.user);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
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
            <div style={{ fontSize: 18, fontWeight: 700, color: '#0f172a' }}>SupportPilot</div>
            <div style={{ fontSize: 12, color: '#94a3b8' }}>技术支持工单系统</div>
          </div>
        </div>
        <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={label}>用户名</label>
            <input
              style={input}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
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
              autoComplete="current-password"
            />
          </div>
          {error && <div style={{ color: '#dc2626', fontSize: 12 }}>{error}</div>}
          <button style={{ ...btn, opacity: loading ? 0.7 : 1 }} disabled={loading} type="submit">
            {loading ? '登录中…' : '登 录'}
          </button>
        </form>
        <div style={{ marginTop: 16, fontSize: 12, color: '#94a3b8', textAlign: 'center' }}>
          默认账号 admin / 123456 ·
          <Link to="/register" style={{ color: '#2563eb', textDecoration: 'none', marginLeft: 4 }}>
            注册账号
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
