import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { registerToastHandler } from '@/api/client';

interface ToastContextValue {
  showToast: (msg: string) => void;
}

const ToastContext = createContext<ToastContextValue>({ showToast: () => {} });

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState('');
  const [show, setShow] = useState(false);
  const timer = useRef<number | null>(null);

  const showToast = useCallback((m: string) => {
    setMsg(m);
    setShow(true);
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setShow(false), 3200);
  }, []);

  // 把 showToast 注册到 api 层，供 request() 在 403 等场景统一弹出提示
  useEffect(() => {
    registerToastHandler(showToast);
    return () => registerToastHandler(null);
  }, [showToast]);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className={`toast ${show ? 'show' : ''}`}>
        <span className="ok">✓</span>
        <span>{msg}</span>
      </div>
    </ToastContext.Provider>
  );
}
