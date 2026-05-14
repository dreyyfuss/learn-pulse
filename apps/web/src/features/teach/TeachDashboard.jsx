import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import KPICard from '../../components/KPICard';
import courseService from '../../services/courseService';
import useAuthStore from '../../store/authStore';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonTableRows } from '../../components/Skeleton';

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

export default function TeachDashboard() {
  const navigate = useNavigate();
  const user = useAuthStore(s => s.user);

  const [courses,   setCourses]   = useState([]);
  const [analytics, setAnalytics] = useState({});
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');

  useEffect(() => {
    setLoading(true);
    courseService.listOwn()
      .then(async data => {
        const items = data.items ?? data.content ?? [];
        setCourses(items);

        const published = items.filter(c => c.status === 'PUBLISHED');
        const results = await Promise.all(
          published.map(c => courseService.analytics(c.id).catch(() => null))
        );
        const map = {};
        published.forEach((c, i) => { if (results[i]) map[c.id] = results[i].aggregate ?? {}; });
        setAnalytics(map);
      })
      .catch(err => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const allAgg          = Object.values(analytics);
  const totalEnrolled   = allAgg.reduce((s, a) => s + (a?.enrolments  ?? 0), 0);
  const totalCompletions= allAgg.reduce((s, a) => s + (a?.completions ?? 0), 0);
  const completionRate  = totalEnrolled > 0 ? Math.round(totalCompletions / totalEnrolled * 100) : 0;
  const draftCount      = courses.filter(c => c.status === 'DRAFT').length;

  const stats = [
    { label: 'Total courses',     value: String(courses.length),              delta: `${draftCount} draft` },
    { label: 'Total enrolments',  value: totalEnrolled.toLocaleString() },
    { label: 'Total completions', value: totalCompletions.toLocaleString() },
    { label: 'Completion rate',   value: `${completionRate}%` },
  ];

  const firstName = user?.firstName || user?.fullName?.split(' ')[0] || 'there';
  const publishedCount = courses.length - draftCount;

  return (
    <div className="main">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
        <div>
          <div className="page-eyebrow">Instructor dashboard</div>
          <h1 className="page-title">{greeting()}, {firstName}.</h1>
          {!loading && !error && (
            <p className="page-lede" style={{ marginBottom: 0 }}>
              {publishedCount > 0
                ? `${publishedCount} course${publishedCount !== 1 ? 's' : ''} live${draftCount > 0 ? `, ${draftCount} in draft` : ''}. ${totalEnrolled.toLocaleString()} learner${totalEnrolled !== 1 ? 's' : ''} enrolled.`
                : 'No courses published yet — create your first one.'}
            </p>
          )}
        </div>
        <button className="btn btn-primary" style={{ marginTop: 8, flexShrink: 0 }} onClick={() => navigate('/teach/courses', { state: { openCreate: true } })}>
          <Icon name="plus" size={15} /> New course
        </button>
      </div>

      <div className="kpi-row" style={{ marginTop: 28 }}>
        {stats.map(s => <KPICard key={s.label} {...s} />)}
      </div>

      <h2 className="section-head" style={{ marginTop: 8 }}>Your courses</h2>

      {error && (
        <p style={{ color: 'var(--danger)', textAlign: 'center', padding: '40px 0' }}>{error}</p>
      )}

      {!error && (
        <div className="table-wrap">
          <div className="table-row head" style={{ gridTemplateColumns: '1fr 110px 110px 110px 140px' }}>
            <div>Course</div><div>Status</div><div>Enrolled</div><div>Completion</div><div>Actions</div>
          </div>

          {loading && (
            <SkeletonTableRows cols="1fr 110px 110px 110px 140px" widths={['70%','55%','50%','50%','80%']} count={4} />
          )}

          {!loading && courses.length === 0 && (
            <div style={{ gridColumn: '1 / -1', padding: '40px 24px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 14 }}>
              No courses yet — create your first one.
            </div>
          )}

          {!loading && courses.map(c => {
            const agg      = analytics[c.id] ?? {};
            const enrolled = c.status === 'PUBLISHED' ? (agg.enrolments ?? '—') : '—';
            const rate     = c.status === 'PUBLISHED' && agg.completionRate != null ? `${agg.completionRate}%` : '—';
            const lessonCount = c.totalLessons != null ? `${c.totalLessons} lesson${c.totalLessons !== 1 ? 's' : ''}` : null;
            const sub = [lessonCount, c.level ?? c.category].filter(Boolean).join(' · ');

            return (
              <div key={c.id} className="table-row body" style={{ gridTemplateColumns: '1fr 110px 110px 110px 140px' }}>
                <div>
                  <div className="row-title">{c.title}</div>
                  {sub && <div className="row-sub">{sub}</div>}
                </div>
                <div>
                  {c.status === 'PUBLISHED' ? <Tag variant="published">Published</Tag> : <Tag variant="draft">Draft</Tag>}
                </div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{enrolled}</div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{rate}</div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button className="btn btn-secondary btn-xs" onClick={() => navigate(`/teach/courses/${c.id}/analytics`)}>View</button>
                  <button className="btn btn-secondary btn-xs" onClick={() => navigate(`/teach/courses/${c.id}/edit`)}>Edit</button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}