import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../../components/Icon';
import { useAuth } from '../../hooks/useAuth';

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!email || !password) { setError('Please fill in all fields.'); return; }
    setError('');
    setLoading(true);
    try {
      await login(email, password);
    } catch (err) {
      setError(err?.data?.message ?? err?.message ?? 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-stage">
        <div>
          <img src="/assets/logo-wordmark-light.svg" height="28" alt="LearnPulse" />
        </div>
        <div style={{ marginTop: 'auto', marginBottom: 'auto' }}>
          <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 48, fontWeight: 500, letterSpacing: '-0.03em', lineHeight: 1.05, color: '#fbf8f3', maxWidth: '14ch', margin: '0 0 20px' }}>
            Quietly confident learning.
          </h1>
          <p className="quote">"I finished a course on the train, in twenty-minute pieces. The certificate was in my inbox before I got home."</p>
        </div>
        <div style={{ fontSize: 12, color: 'rgba(251,248,243,.4)' }}>© 2026 LearnPulse</div>
      </div>

      <div className="auth-form-wrap">
        <form className="auth-form" onSubmit={handleSubmit}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 28 }}>
            <img src="/assets/logo-mark.svg" width="32" height="32" alt="" />
            <span style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 500, letterSpacing: '-0.01em', color: 'var(--ink)' }}>LearnPulse</span>
          </div>
          <h2>Welcome back</h2>
          <p className="sub">Pick up where you left off.</p>

          <div className="field">
            <label>Email address</label>
            <input className="input" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" />
          </div>
          <div className="field">
            <label>Password</label>
            <input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password" autoComplete="current-password" />
          </div>

          {error && <p style={{ fontSize: 13, color: 'var(--danger)', marginBottom: 12 }}>{error}</p>}

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
            <a href="#" style={{ fontSize: 13, color: 'var(--indigo)' }}>Forgot password?</a>
          </div>

          <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} type="submit" disabled={loading}>
            {loading ? 'Signing in…' : 'Continue'} <Icon name="arrow-right" size={16} />
          </button>

          <div style={{ textAlign: 'center', fontSize: 13, color: 'var(--ink-3)', marginTop: 20 }}>
            New here?{' '}
            <Link to="/register" style={{ color: 'var(--indigo)' }}>Create an account</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
