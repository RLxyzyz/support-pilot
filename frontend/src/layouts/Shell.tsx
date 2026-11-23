import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useToast } from '@/components/Toast';
import { api, getStoredUser, clearAuth, isAdmin } from '@/api/client';

// 与原型一致的侧边栏 SVG 图标
const IconTicket = (
  <svg viewBox="0 0 24 24">
    <rect height="16" rx="2" width="18" x="3" y="4" />
    <path d="M3 9h18M8 4v16" />
  </svg>
);
const IconCustomer = (
  <svg viewBox="0 0 24 24">
    <path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-5h6v5" />
  </svg>
);
const IconBoard = (
  <svg viewBox="0 0 24 24">
    <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
  </svg>
);
const IconBook = (
  <svg viewBox="0 0 24 24">
    <path d="M4 5a2 2 0 012-2h11a1 1 0 011 1v16a1 1 0 01-1 1H6a2 2 0 01-2-2z" />
    <path d="M8 7h7M8 11h7" />
  </svg>
);
const IconEval = (
  <svg viewBox="0 0 24 24">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);
const IconSettings = (
  <svg viewBox="0 0 24 24">
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.9-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.9 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.9.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.9V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z" />
  </svg>
);
const IconLog = (
  <svg viewBox="0 0 24 24">
    <path d="M5 3h11l3 3v15H5z" />
    <path d="M8 8h8M8 12h8M8 16h5" />
  </svg>
);
const IconUser = (
  <svg viewBox="0 0 24 24">
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21v-1a8 8 0 0116 0v1" />
  </svg>
);
const IconBell = (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M18 8a6 6 0 10-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
    <path d="M13.7 21a2 2 0 01-3.4 0" />
  </svg>
);
const IconRule = (
  <svg viewBox="0 0 24 24">
    <path d="M4 6h16M4 12h16M4 18h10" />
    <circle cx="18" cy="18" r="2.5" />
  </svg>
);
const IconSync = (
  <svg viewBox="0 0 24 24">
    <path d="M21 12a9 9 0 01-15.5 6.2M3 12a9 9 0 0115.5-6.2" />
    <path d="M3 20v-5h5M21 4v5h-5" />
  </svg>
);

export default function Shell() {
  const { showToast } = useToast();
  const location = useLocation();
  const navigate = useNavigate();
  const [gs, setGs] = useState('');
  const user = getStoredUser();
  const [stats, setStats] = useState<{ p1Open: number; total: number }>({ p1Open: 0, total: 0 });
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    api
      .getQuickStats()
      .then((s) => setStats({ p1Open: s.p1Open, total: s.total }))
      .catch(() => {});
  }, []);

  // 未读数：首次加载 + 每 30 秒轮询 + 页面内标记已读后由 'sp:notify' 事件即时刷新
  useEffect(() => {
    let alive = true;
    const load = () => {
      api
        .getUnreadCount()
        .then((r) => {
          if (alive) setUnread(r.count || 0);
        })
        .catch(() => {});
    };
    load();
    const timer = window.setInterval(load, 30000);
    window.addEventListener('sp:notify', load);
    return () => {
      alive = false;
      window.clearInterval(timer);
      window.removeEventListener('sp:notify', load);
    };
  }, [location.pathname]);

  const logout = () => {
    clearAuth();
    navigate('/login', { replace: true });
  };

  const crumbMap: Record<string, string> = {
    '/': '工单',
    '/board': '效果看板',
    '/kb': '知识库',
    '/logs': '操作日志',
    '/customers': '客户',
    '/users': '用户管理',
    '/profile': '个人设置',
    '/notifications': '通知中心',
    '/notify-rules': '通知规则',
    '/sla': 'SLA 策略',
    '/settings/smart': '智能服务配置',
    '/settings/sync': '同步配置'
  };
  let crumb = crumbMap[location.pathname] || '工单';
  const detailMatch = location.pathname.match(/^\/tickets\/(.+)$/);
  if (detailMatch) crumb = '工单 ' + (detailMatch[1].startsWith('#') ? '' : '#') + detailMatch[1];
  const custDetailMatch = location.pathname.match(/^\/customers\/(\d+)$/);
  if (custDetailMatch) crumb = '客户详情';

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">S</div>
          <div className="brand-name">SupportPilot</div>
          <div className="brand-tag">BETA</div>
        </div>
        <div className="nav">
          <div className="nav-label">工作台</div>
          <NavLink to="/" end className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
            {IconTicket}
            工单
            {stats.p1Open > 0 ? <span className="nav-count">{stats.p1Open}</span> : null}
          </NavLink>
          <NavLink to="/customers" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
            {IconCustomer}
            客户
          </NavLink>
          <NavLink to="/board" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
            {IconBoard}
            效果看板
          </NavLink>
          <div className="nav-label">知识</div>
          <NavLink to="/kb" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
            {IconBook}
            知识库
          </NavLink>
          <NavLink
            to="/settings/smart"
            className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}
          >
            {IconEval}
            评测集
          </NavLink>
          <div className="nav-label">系统</div>
          {isAdmin() && (
            <NavLink to="/users" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
              {IconUser}
              用户管理
            </NavLink>
          )}
          {isAdmin() && (
            <NavLink to="/logs" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
              {IconLog}
              操作日志
            </NavLink>
          )}
          {isAdmin() && (
            <NavLink to="/notify-rules" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
              {IconRule}
              通知规则
            </NavLink>
          )}
          <NavLink to="/sla" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
            {IconEval}
            SLA 策略
          </NavLink>
          <NavLink
            to="/settings/smart"
            className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}
          >
            {IconRule}
            智能服务
          </NavLink>
          <NavLink
            to="/settings/sync"
            className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}
          >
            {IconSync}
            同步配置
          </NavLink>
          <NavLink to="/profile" className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
            {IconSettings}
            个人设置
          </NavLink>
        </div>
        {stats.total > 0 && (
          <div className="sidebar-tip">
            <div className="t">✦ AI Copilot 已就绪</div>
            <div className="d">当前租户共 {stats.total} 条工单，{stats.p1Open} 条 P1 待处理</div>
          </div>
        )}
        <div className="sidebar-foot">
          <div className="avatar">{(user?.name || 'U').charAt(0)}</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="foot-name">{user?.name || '未登录'}</div>
            <div className="foot-role">{isAdmin() ? '管理员' : '支持工程师'}</div>
          </div>
          <button className="btn ghost sm" onClick={logout}>
            退出
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="crumb">
            工作台 / <b>{crumb}</b>
          </div>
          <span className="demo-pill">演示环境</span>
          <div className="top-search">
            <svg fill="none" height="13" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="13">
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.5-3.5" />
            </svg>
            <input
              placeholder="搜索工单、客户、文档…（Enter）"
              value={gs}
              onChange={(e) => setGs(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && gs.trim()) {
                  // 跳转到工单列表并通过 URL 传关键词，列表页挂载时读取并触发搜索
                  navigate('/?keyword=' + encodeURIComponent(gs.trim()));
                  setGs('');
                }
              }}
            />
            <kbd>⌘K</kbd>
          </div>
          <button
            className="bell"
            title={unread > 0 ? `通知中心（${unread} 条未读）` : '通知中心'}
            aria-label="通知中心"
            onClick={() => navigate('/notifications')}
          >
            {IconBell}
            {unread > 0 && <span className="bell-badge">{unread > 99 ? '99+' : unread}</span>}
          </button>
        </header>
        <div className="content">
          <div className="view">
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  );
}
