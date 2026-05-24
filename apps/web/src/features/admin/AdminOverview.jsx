import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import KPICard from '../../components/KPICard';
import adminService from '../../services/adminService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonKPICard } from '../../components/Skeleton';

export default function AdminOverview() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    adminService.getAnalytics()
      .then(setData)
      .catch(e => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  }, []);

  const u = data?.users ?? {};
  const c = data?.courses ?? {};
  const e = data?.enrolments ?? {};
  const rate = e.completionRate != null ? `${e.completionRate}%` : '—';

  const kpis = [
    { label: 'Total learners',           value: loading ? '—' : (u.byRole?.LEARNER   != null ? String(u.byRole.LEARNER)   : '—'), variant: 'indigo' },
    { label: 'Total instructors',        value: loading ? '—' : (u.byRole?.INSTRUCTOR != null ? String(u.byRole.INSTRUCTOR) : '—'), variant: 'teal'  },
    { label: 'Total courses',            value: loading ? '—' : (c.total             != null ? String(c.total)             : '—'), variant: 'coral'  },
    { label: 'Total enrolments',         value: loading ? '—' : (e.total             != null ? String(e.total)             : '—'), variant: 'indigo' },
    { label: 'Completions',              value: loading ? '—' : (e.completed         != null ? String(e.completed)         : '—'), variant: 'green'  },
    { label: 'Platform completion rate', value: loading ? '—' : rate,                                                               variant: 'green'  },
  ];

  return (
    <div className="main">
      <div className="page-eyebrow">Admin</div>
      <h1 className="page-title">Platform overview</h1>
      <p className="page-lede">LearnPulse at a glance.</p>

      {error && (
        <div style={{
          background: 'var(--danger-bg)', border: '1px solid var(--coral-200)',
          color: 'var(--danger)', borderRadius: 8, padding: '12px 16px',
          marginBottom: 24, fontSize: 14, display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <Icon name="alert-circle" size={16} color="var(--danger)" />
          {error}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 32 }}>
        {loading
          ? [0,1,2,3,4,5].map(i => <SkeletonKPICard key={i} />)
          : kpis.map(s => <KPICard key={s.label} {...s} />)}
      </div>

      <h2 className="section-head" style={{ marginTop: 0 }}>Quick actions</h2>
      <div style={{ display: 'flex', gap: 12 }}>
        <button className="btn btn-secondary" onClick={() => navigate('/admin/users')}>
          <Icon name="users" size={15} /> Manage users
        </button>
        <button className="btn btn-secondary" onClick={() => navigate('/admin/courses')}>
          <Icon name="book-open" size={15} /> Manage courses
        </button>
        <button className="btn btn-secondary" onClick={() => navigate('/admin/enrolments')}>
          <Icon name="user-plus" size={15} /> Manage enrolments
        </button>
      </div>
    </div>
  );
}
