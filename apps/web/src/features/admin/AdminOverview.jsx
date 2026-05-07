import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import KPICard from '../../components/KPICard';

const STATS = [
  { label: 'Total learners',           value: '4,821', delta: '+12% this month', dir: 'up' },
  { label: 'Total instructors',        value: '38',    delta: '+2 this month',   dir: 'up' },
  { label: 'Total courses',            value: '24',    delta: '3 in draft',      dir: 'up' },
  { label: 'Total enrolments',         value: '12,340',delta: '+8% this month',  dir: 'up' },
  { label: 'Completions',              value: '7,218', delta: '+11% this month', dir: 'up' },
  { label: 'Platform completion rate', value: '58%',   delta: '−2% vs last month', dir: 'down' },
];

export default function AdminOverview() {
  const navigate = useNavigate();
  return (
    <div className="main">
      <div className="page-eyebrow">Admin</div>
      <h1 className="page-title">Platform overview</h1>
      <p className="page-lede">LearnPulse at a glance — 6 May 2026.</p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 32 }}>
        {STATS.map(s => <KPICard key={s.label} {...s} />)}
      </div>
      <h2 className="section-head" style={{ marginTop: 0 }}>Quick actions</h2>
      <div style={{ display: 'flex', gap: 12 }}>
        <button className="btn btn-secondary" onClick={() => navigate('/admin/users')}><Icon name="users" size={15} /> Manage users</button>
        <button className="btn btn-secondary" onClick={() => navigate('/admin/courses')}><Icon name="book-open" size={15} /> Manage courses</button>
        <button className="btn btn-secondary" onClick={() => navigate('/admin/enrolments')}><Icon name="user-plus" size={15} /> Manage enrolments</button>
      </div>
    </div>
  );
}
