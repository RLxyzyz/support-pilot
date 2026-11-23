import { useEffect, type ReactNode } from 'react';

interface ModalProps {
  open: boolean;
  title: string;
  subtitle?: string;
  icon?: string;
  iconClass?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}

export default function Modal({ open, title, subtitle, icon, iconClass, onClose, children, footer, width }: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="modal-wrap show">
      <div className="modal-scrim" onClick={onClose} />
      <div className="modal" style={width ? { maxWidth: width } : undefined} role="dialog" aria-modal="true">
        <div className="modal-head">
          {icon ? <div className={'modal-ico ' + (iconClass || '')}>{icon}</div> : null}
          <div style={{ minWidth: 0 }}>
            <div className="modal-title">{title}</div>
            {subtitle ? <div className="modal-sub">{subtitle}</div> : null}
          </div>
          <button className="modal-x" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer ? <div className="modal-foot">{footer}</div> : null}
      </div>
    </div>
  );
}
