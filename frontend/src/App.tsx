import { Routes, Route, Navigate } from 'react-router-dom';
import type { ReactElement } from 'react';
import { ToastProvider } from '@/components/Toast';
import Shell from '@/layouts/Shell';
import TicketList from '@/pages/TicketList';
import TicketDetail from '@/pages/TicketDetail';
import Board from '@/pages/Board';
import KnowledgeBase from '@/pages/KnowledgeBase';
import OperationLogs from '@/pages/OperationLogs';
import CustomerList from '@/pages/CustomerList';
import CustomerDetail from '@/pages/CustomerDetail';
import UserManagement from '@/pages/UserManagement';
import Profile from '@/pages/Profile';
import Notifications from '@/pages/Notifications';
import NotifyRules from '@/pages/NotifyRules';
import SlaSettings from '@/pages/SlaSettings';
import SmartSettings from '@/pages/SmartSettings';
import SyncSettings from '@/pages/SyncSettings';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import { getToken } from '@/api/client';

/** 路由守卫：无 token 则跳转登录页 */
function RequireAuth({ children }: { children: ReactElement }) {
  return getToken() ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <ToastProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route element={<RequireAuth><Shell /></RequireAuth>}>
          <Route path="/" element={<TicketList />} />
          <Route path="/tickets/:id" element={<TicketDetail />} />
          <Route path="/customers" element={<CustomerList />} />
          <Route path="/customers/:id" element={<CustomerDetail />} />
          <Route path="/board" element={<Board />} />
          <Route path="/kb" element={<KnowledgeBase />} />
          <Route path="/users" element={<UserManagement />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/notify-rules" element={<NotifyRules />} />
          <Route path="/sla" element={<SlaSettings />} />
          <Route path="/settings/smart" element={<SmartSettings />} />
          <Route path="/settings/sync" element={<SyncSettings />} />
          <Route path="/logs" element={<OperationLogs />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ToastProvider>
  );
}
