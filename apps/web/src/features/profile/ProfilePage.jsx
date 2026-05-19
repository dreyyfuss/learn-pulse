import { useState, useEffect } from 'react';
import Icon from '../../components/Icon';
import Notification from '../../components/Notification';
import userService from '../../services/userService';
import useAuthStore from '../../store/authStore';

export default function ProfilePage() {
  const { user, setAuth, token, refreshToken } = useAuthStore();

  const [fullName, setFullName]     = useState('');
  const [password, setPassword]     = useState('');
  const [confirm, setConfirm]       = useState('');
  const [saving, setSaving]         = useState(false);
  const [toast, setToast]           = useState('');
  const [toastType, setToastType]   = useState('');

  useEffect(() => {
    const name = user?.firstName ? `${user.firstName} ${user.lastName ?? ''}`.trim() : '';
    setFullName(name);
  }, [user]);

  const showToast = (msg, type = 'success') => {
    setToast(msg);
    setToastType(type);
    setTimeout(() => { setToast(''); setToastType(''); }, 3500);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (password && password !== confirm) {
      showToast('Passwords do not match.', 'error');
      return;
    }
    if (password && password.length < 8) {
      showToast('Password must be at least 8 characters.', 'error');
      return;
    }
    setSaving(true);
    try {
      const payload = {};
      if (fullName.trim()) payload.fullName = fullName.trim();
      if (password)        payload.password = password;
      const res = await userService.updateMe(payload);
      const updated = res?.data ?? res;
      if (updated?.fullName) {
        const [first, ...rest] = updated.fullName.trim().split(' ');
        setAuth({ ...user, firstName: first, lastName: rest.join(' ') }, token, refreshToken);
      }
      setPassword('');
      setConfirm('');
      showToast('Profile updated.');
    } catch (err) {
      showToast(err?.data?.message ?? err?.message ?? 'Update failed.', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="main" style={{ maxWidth: 520 }}>
      <div className="page-eyebrow">Account</div>
      <h1 className="page-title">Your profile.</h1>
      <p className="page-lede">Update your name or change your password.</p>

      <form onSubmit={handleSave} style={{ marginTop: 32 }}>
        <div className="field">
          <label>Email address</label>
          <input
            className="input"
            type="email"
            value={user?.email ?? ''}
            disabled
            style={{ opacity: 0.6, cursor: 'not-allowed' }}
          />
        </div>

        <div className="field">
          <label>Full name</label>
          <input
            className="input"
            type="text"
            value={fullName}
            onChange={e => setFullName(e.target.value)}
            placeholder="Your full name"
            maxLength={120}
          />
        </div>

        <hr style={{ border: 0, borderTop: '1px solid var(--rule)', margin: '28px 0' }} />
        <p style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 16 }}>
          Leave password fields blank to keep your current password.
        </p>

        <div className="field">
          <label>New password</label>
          <input
            className="input"
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="At least 8 characters"
            autoComplete="new-password"
          />
        </div>

        <div className="field">
          <label>Confirm new password</label>
          <input
            className="input"
            type="password"
            value={confirm}
            onChange={e => setConfirm(e.target.value)}
            placeholder="Repeat password"
            autoComplete="new-password"
          />
        </div>

        <button
          className="btn btn-primary"
          type="submit"
          disabled={saving}
          style={{ marginTop: 8 }}
        >
          {saving ? 'Saving…' : <><Icon name="check" size={15} /> Save changes</>}
        </button>
      </form>

      {toast && (
        <Notification style={toastType === 'error' ? { background: 'var(--danger)' } : undefined}>
          {toast}
        </Notification>
      )}
    </div>
  );
}
