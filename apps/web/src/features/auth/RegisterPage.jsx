import { useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../../components/Icon';
import { useAuth } from '../../hooks/useAuth';

export default function RegisterPage() {
  const { register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState({ learn: true, teach: false });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const toggle = (role) => setRoles(r => ({ ...r, [role]: !r[role] }));

  async function handleSubmit(e) {
    e.preventDefault();
    if (!name || !email || !password) { setError('Please fill in all fields.'); return; }
    if (!roles.learn && !roles.teach) { setError('Please select at least one role.'); return; }
    setError('');
    setLoading(true);
    try {
      const [firstName, ...rest] = name.trim().split(' ');
      const roleValue = roles.learn && roles.teach ? 'BOTH' : roles.teach ? 'INSTRUCTOR' : 'LEARNER';
      await register({ firstName, lastName: rest.join(' ') || '', email, password, role: roleValue });
    } catch (err) {
      setError(err?.data?.message ?? err?.message ?? 'Registration failed. Please try again.');
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
          <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 44, fontWeight: 500, letterSpacing: '-0.03em', lineHeight: 1.05, color: '#fbf8f3', maxWidth: '16ch', margin: '0 0 16px' }}>
            Join a library, not a bootcamp.
          </h1>
          <p className="quote">Thoughtful courses for people who take learning seriously. No streaks, no badges — just good material.</p>
        </div>
        <div style={{ fontSize: 12, color: 'rgba(251,248,243,.4)' }}>© 2026 LearnPulse</div>
      </div>

      <div className="auth-form-wrap">
        <form className="auth-form" onSubmit={handleSubmit}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 28 }}>
            <img src="/assets/logo-mark.svg" width="32" height="32" alt="" />
            <span style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 500, letterSpacing: '-0.01em', color: 'var(--ink)' }}>LearnPulse</span>
          </div>
          <h2>Create an account</h2>
          <p className="sub">Takes about 30 seconds.</p>

          <div className="field">
            <label>Full name</label>
            <input className="input" type="text" value={name} onChange={e => setName(e.target.value)} placeholder="Alex Reyes" autoComplete="name" />
          </div>
          <div className="field">
            <label>Email address</label>
            <input className="input" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" />
          </div>
          <div className="field">
            <label>Password</label>
            <input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Min. 8 characters" autoComplete="new-password" minLength={8} />
          </div>

          <div className="field">
            <label>I want to…</label>
            <div className="role-select">
              <div className={`role-option${roles.learn ? ' selected' : ''}`} onClick={() => toggle('learn')}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 3 }}>
                  <span className="ro-title">Learn</span>
                  {roles.learn && <Icon name="check-circle" size={15} color="var(--indigo)" />}
                </div>
                <div className="ro-sub">Browse and enrol in courses</div>
              </div>
              <div className={`role-option${roles.teach ? ' selected' : ''}`} onClick={() => toggle('teach')}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 3 }}>
                  <span className="ro-title">Teach</span>
                  {roles.teach && <Icon name="check-circle" size={15} color="var(--indigo)" />}
                </div>
                <div className="ro-sub">Create and publish courses</div>
              </div>
            </div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4 }}>You can hold both roles simultaneously.</div>
          </div>

          {error && <p style={{ fontSize: 13, color: 'var(--danger)', marginBottom: 12 }}>{error}</p>}

          <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} type="submit" disabled={loading}>
            {loading ? 'Creating account…' : 'Create account'} <Icon name="arrow-right" size={16} />
          </button>

          <div style={{ textAlign: 'center', fontSize: 13, color: 'var(--ink-3)', marginTop: 20 }}>
            Already have an account?{' '}
            <Link to="/login" style={{ color: 'var(--indigo)' }}>Sign in</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
