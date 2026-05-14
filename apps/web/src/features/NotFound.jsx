import { useNavigate } from 'react-router-dom';
import Icon from '../components/Icon';

export default function NotFound() {
  const navigate = useNavigate();
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      minHeight: '100vh', background: 'var(--paper)', textAlign: 'center', padding: 40,
    }}>
      <div style={{
        fontFamily: 'var(--font-display)', fontSize: 96, fontWeight: 500,
        letterSpacing: '-0.04em', lineHeight: 1, color: 'var(--ink)', marginBottom: 12,
      }}>
        404
      </div>
      <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, fontWeight: 500, letterSpacing: '-0.02em', margin: '0 0 8px' }}>
        Page not found
      </h1>
      <p style={{ fontSize: 16, color: 'var(--ink-3)', maxWidth: '36ch', lineHeight: 1.55, margin: '0 0 32px' }}>
        The page you're looking for doesn't exist or you don't have access.
      </p>
      <button className="btn btn-primary" onClick={() => navigate('/learn/dashboard')}>
        <Icon name="arrow-left" size={15} /> Go home
      </button>
    </div>
  );
}