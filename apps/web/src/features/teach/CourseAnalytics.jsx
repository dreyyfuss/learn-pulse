import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import KPICard from '../../components/KPICard';
import { COURSES, LEARNER_TABLE } from '../../data/mockData';

export default function CourseAnalytics() {
  const { id } = useParams();
  const course = COURSES.find(c => c.id === id) || COURSES[0];
  const [sortKey, setSortKey] = useState('name');
  const [sortDir, setSortDir] = useState('asc');

  const handleSort = (key) => {
    if (sortKey === key) setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    else { setSortKey(key); setSortDir('asc'); }
  };

  const sorted = [...LEARNER_TABLE].sort((a, b) => {
    const va = a[sortKey] || '', vb = b[sortKey] || '';
    return sortDir === 'asc' ? va.localeCompare(vb) : vb.localeCompare(va);
  });

  const stats = [
    { label: 'Enrolments', value: String(course.enrolled) },
    { label: 'Completions', value: String(course.completions) },
    { label: 'Completion rate', value: `${course.completionRate}%` },
    { label: 'Active learners', value: String(LEARNER_TABLE.filter(l => l.status === 'in-progress').length) },
  ];

  const SortIcon = ({ k }) => (
    <Icon name={sortKey === k ? (sortDir === 'asc' ? 'chevron-up' : 'chevron-down') : 'chevrons-up-down'} size={12} color="var(--ink-4)" style={{ marginLeft: 4 }} />
  );

  const DROP_DATA = [
    ['M01·L01 What is a pointer, really?', 624], ['M01·L02 Walking a list, step by step', 612],
    ['M01·L03 When lists beat arrays', 588],       ['M01·L04 Drawing it on paper', 561],
    ['M02·L01 Inserting at the head', 510],        ['M02·L02 Inserting in the middle', 478],
    ['M02·L03 Removing a node', 462],              ['M02·L04 A short exercise', 312],
    ['M03·L01 A list with branches', 298],
  ];

  return (
    <div className="main">
      <div className="page-eyebrow">Analytics</div>
      <h1 className="page-title">{course.title}</h1>
      <p className="page-lede">Most drop-off happens in module 2, lesson 4 — the exercise. Worth reviewing.</p>

      <div className="kpi-row">{stats.map(s => <KPICard key={s.label} {...s} />)}</div>

      <div style={{ background: '#fff', border: '1px solid var(--rule)', borderRadius: 12, padding: '22px 24px', marginBottom: 28 }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, fontWeight: 500, letterSpacing: '-0.01em', marginBottom: 4 }}>Lesson-by-lesson progress</div>
        <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 18 }}>Learners who have reached each lesson.</div>
        <div style={{ display: 'grid', gap: 10 }}>
          {DROP_DATA.map(([n, v]) => (
            <div key={n} style={{ display: 'grid', gridTemplateColumns: '280px 1fr 52px', alignItems: 'center', gap: 14 }}>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-2)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{n}</div>
              <div style={{ height: 12, background: 'var(--paper-3)', borderRadius: 999 }}>
                <div style={{ height: '100%', width: `${(v / 624) * 100}%`, background: v < 400 ? 'var(--coral)' : 'var(--indigo)', borderRadius: 999 }} />
              </div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12, textAlign: 'right', color: 'var(--ink-3)' }}>{v}</div>
            </div>
          ))}
        </div>
      </div>

      <h2 className="section-head" style={{ marginTop: 0 }}>Learner progress</h2>
      <div className="table-wrap analytics-table">
        <div className="table-row head" style={{ gridTemplateColumns: '1.4fr 1.6fr 100px 1fr 110px 110px' }}>
          {[['name','Name'],['email','Email'],['status','Status'],['currentLesson','Current lesson'],['enrolled','Enrolled'],['completed','Completed']].map(([k, lbl]) => (
            <div key={k} style={{ display: 'flex', alignItems: 'center', cursor: 'pointer', userSelect: 'none' }} onClick={() => handleSort(k)}>
              {lbl}<SortIcon k={k} />
            </div>
          ))}
        </div>
        {sorted.map(l => (
          <div key={l.id} className="table-row body" style={{ gridTemplateColumns: '1.4fr 1.6fr 100px 1fr 110px 110px' }}>
            <div style={{ fontWeight: 500, fontSize: 14, color: 'var(--ink)' }}>{l.name}</div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{l.email}</div>
            <div>{l.status === 'completed' ? <Tag variant="completed">Completed</Tag> : <Tag variant="in-progress">In progress</Tag>}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-2)' }}>{l.currentLesson}</div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{l.enrolled}</div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{l.completed || '—'}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
