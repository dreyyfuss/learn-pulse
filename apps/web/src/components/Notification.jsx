import Icon from './Icon';

export default function Notification({ children, onClose }) {
  return (
    <div style={{
      position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)', maxWidth: 'calc(100vw - 32px)',
      background: 'var(--ink)', color: '#fbf8f3',
      padding: '12px 20px', borderRadius: 10, fontSize: 14,
      display: 'flex', alignItems: 'center', gap: 12,
      boxShadow: 'var(--shadow-3)', zIndex: 9999,
      animation: 'slideUp 200ms var(--ease-out)',
    }}>
      {children}
      {onClose && (
        <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'rgba(251,248,243,.6)', cursor: 'pointer', padding: 0 }}>
          <Icon name="x" size={14} />
        </button>
      )}
    </div>
  );
}
