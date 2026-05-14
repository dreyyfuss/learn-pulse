import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import KPICard from '../../components/KPICard';

const STATS = [
  { label: 'Total courses',     value: '4',     delta: '1 draft',         dir: 'up' },
  { label: 'Total enrolments',  value: '1,284', delta: '48 this week',    dir: 'up' },
  { label: 'Total completions', value: '1,042', delta: '29 this week',    dir: 'up' },
  { label: 'Completion rate',   value: '67%',   delta: '2% vs last month',dir: 'down' },
];

const COURSE_ROWS = [
  { id: 'c1', title: 'Data structures, in plain English', sub: '12 lessons · Intermediate', status: 'published', enrolled: 624, rate: '71%' },
  { id: 'c2', title: 'A quiet course on layout',          sub: '8 lessons · Beginner',      status: 'published', enrolled: 418, rate: '83%' },
  { id: 'c3', title: 'Plain language at work',            sub: '9 lessons · Beginner',      status: 'published', enrolled: 242, rate: '52%' },
  { id: 'c4', title: 'Algorithms without the panic',      sub: '14 lessons · Advanced',     status: 'draft',     enrolled: 0,   rate: '—'   },
];

export default function TeachDashboard() {
  const navigate = useNavigate();

  return (
    <div className="main">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
        <div>
          <div className="page-eyebrow">Instructor dashboard</div>
          <h1 className="page-title">Good morning, Marta.</h1>
          <p className="page-lede" style={{ marginBottom: 0 }}>Three courses live, one in draft. 1,284 learners enrolled this month — up 12% from April.</p>
        </div>
        <button className="btn btn-primary" style={{ marginTop: 8, flexShrink: 0 }} onClick={() => navigate('/teach/courses', { state: { openCreate: true } })}>
          <Icon name="plus" size={15} /> New course
        </button>
      </div>

      <div className="kpi-row" style={{ marginTop: 28 }}>
        {STATS.map(s => <KPICard key={s.label} {...s} />)}
      </div>

      <h2 className="section-head" style={{ marginTop: 8 }}>Your courses</h2>
      <div className="table-wrap">
        <div className="table-row head" style={{ gridTemplateColumns: '1fr 110px 110px 110px 140px' }}>
          <div>Course</div><div>Status</div><div>Enrolled</div><div>Completion</div><div>Actions</div>
        </div>
        {COURSE_ROWS.map(c => (
          <div key={c.id} className="table-row body" style={{ gridTemplateColumns: '1fr 110px 110px 110px 140px' }}>
            <div>
              <div className="row-title">{c.title}</div>
              <div className="row-sub">{c.sub}</div>
            </div>
            <div>
              {c.status === 'published' ? <Tag variant="published">Published</Tag> : <Tag variant="draft">Draft</Tag>}
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{c.enrolled || '—'}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{c.rate}</div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button className="btn btn-secondary btn-xs" onClick={() => navigate(`/teach/courses/${c.id}/analytics`)}>View</button>
              <button className="btn btn-secondary btn-xs" onClick={() => navigate(`/teach/courses/${c.id}/edit`)}>Edit</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
