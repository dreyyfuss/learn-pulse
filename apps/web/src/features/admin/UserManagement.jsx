import { useState, useEffect } from 'react';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Avatar from '../../components/Avatar';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import adminService from '../../services/adminService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonTableRows } from '../../components/Skeleton';

const fmt = (iso) => iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
const lower = (s) => (s ?? '').toLowerCase();

function normalize(u) {
  return {
    ...u,
    name: u.fullName,
    roles: (u.roles ?? []).map(lower),
    status: lower(u.status),
    registered: fmt(u.createdAt),
  };
}

const ROLE_PARAMS = { All: undefined, Learner: 'LEARNER', Instructor: 'INSTRUCTOR', Admin: 'ADMIN' };
const GRID = '1.4fr 1.6fr 140px 80px 110px 160px';

export default function UserManagement() {
  const [users, setUsers]       = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState(null);
  const [search, setSearch]     = useState('');
  const [roleFilter, setRoleFilter] = useState('All');
  const [toast, setToast]       = useState('');
  const [confirmModal, setConfirmModal] = useState(null);

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

  const fetchUsers = (role) => {
    setLoading(true);
    setError(null);
    adminService.getUsers({ size: 100, ...(role ? { role } : {}) })
      .then(page => setUsers((page.content ?? []).map(normalize)))
      .catch(e => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchUsers(); }, []);

  const handleRoleFilter = (r) => {
    setRoleFilter(r);
    fetchUsers(ROLE_PARAMS[r]);
  };

  const filtered = users.filter(u => {
    if (!search) return true;
    const q = search.toLowerCase();
    return u.name?.toLowerCase().includes(q) || u.email?.toLowerCase().includes(q);
  });

  const handleAction = (userId, action) => {
    const call = action === 'promote'   ? adminService.promote(userId)
               : action === 'suspend'   ? adminService.suspend(userId)
               : adminService.reinstate(userId);

    call
      .then(updated => {
        setUsers(us => us.map(u => u.id !== userId ? u : normalize(updated)));
        showToast({ suspend: 'User suspended.', reinstate: 'User reinstated.', promote: 'User promoted to admin.' }[action] ?? 'Done.');
      })
      .catch(e => showToast(getErrorMessage(e)))
      .finally(() => setConfirmModal(null));
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Admin · Users</div>
      <h1 className="page-title">User management</h1>

      {error && (
        <div style={{ background: 'var(--danger-bg)', border: '1px solid var(--coral-200)', color: 'var(--danger)', borderRadius: 8, padding: '12px 16px', marginBottom: 16, fontSize: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="alert-circle" size={16} color="var(--danger)" />{error}
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid var(--rule)', borderRadius: 8, padding: '8px 14px', flex: 1, maxWidth: 360 }}>
          <Icon name="search" size={15} color="var(--ink-3)" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name or email…"
            style={{ border: 0, outline: 0, background: 'transparent', font: 'inherit', fontSize: 14, flex: 1, color: 'var(--ink)' }} />
        </div>
        <div className="filter-row" style={{ margin: 0 }}>
          {['All', 'Learner', 'Instructor', 'Admin'].map(r => (
            <span key={r} className={`chip${roleFilter === r ? ' active' : ''}`}
              onClick={() => handleRoleFilter(r)} style={{ padding: '6px 12px', fontSize: 12 }}>{r}</span>
          ))}
        </div>
      </div>

      <div className="table-wrap admin-users-table">
        <div className="table-row head" style={{ gridTemplateColumns: GRID }}>
          <div>Name</div><div>Email</div><div>Roles</div><div>Status</div><div>Registered</div><div>Actions</div>
        </div>

        {loading && <SkeletonTableRows cols={GRID} widths={['70%','70%','70%','60%','65%','70%']} count={4} />}

        {!loading && filtered.map(u => (
          <div key={u.id} className="table-row body" style={{ gridTemplateColumns: GRID }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Avatar name={u.name} size={28} />
              <span style={{ fontWeight: 500, fontSize: 14 }}>{u.name}</span>
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{u.email}</div>
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
              {u.roles.map(r => (
                <Tag key={r} variant={r === 'admin' ? 'admin' : r === 'instructor' ? 'instructor' : 'learner'}>
                  {r.charAt(0).toUpperCase() + r.slice(1)}
                </Tag>
              ))}
            </div>
            <div><Tag variant={u.status}>{u.status.charAt(0).toUpperCase() + u.status.slice(1)}</Tag></div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{u.registered}</div>
            <div style={{ display: 'flex', gap: 5 }}>
              {!u.roles.includes('admin') && (
                <button className="btn btn-secondary btn-xs"
                  onClick={() => setConfirmModal({ userId: u.id, action: 'promote', label: `Promote ${u.name} to admin?`, desc: 'Admins have full access to platform settings, users, and courses.' })}>
                  Promote
                </button>
              )}
              {u.status === 'active'
                ? <button className="btn btn-danger btn-xs"
                    onClick={() => setConfirmModal({ userId: u.id, action: 'suspend', label: `Suspend ${u.name}?`, desc: 'They will lose access to the platform immediately.' })}>
                    Suspend
                  </button>
                : <button className="btn btn-secondary btn-xs" onClick={() => handleAction(u.id, 'reinstate')}>Reinstate</button>
              }
            </div>
          </div>
        ))}

        {!loading && !error && filtered.length === 0 && (
          <div style={{ gridColumn: '1 / -1', padding: '40px 24px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 14 }}>
            No users found.
          </div>
        )}
      </div>

      {confirmModal && (
        <Modal title={confirmModal.label} onClose={() => setConfirmModal(null)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setConfirmModal(null)}>Cancel</button>
              <button className={`btn ${confirmModal.action === 'suspend' ? 'btn-danger' : 'btn-primary'}`}
                onClick={() => handleAction(confirmModal.userId, confirmModal.action)}>
                {confirmModal.action.charAt(0).toUpperCase() + confirmModal.action.slice(1)}
              </button>
            </>
          }
        ><p>{confirmModal.desc}</p></Modal>
      )}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
