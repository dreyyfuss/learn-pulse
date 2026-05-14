import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import KPICard from '../../components/KPICard';
import courseService from '../../services/courseService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonTableRows } from '../../components/Skeleton';

// ─── Donut chart ────────────────────────────────────────────────────────────
function DonutChart({ pct = 0, size = 140, stroke = 18 }) {
  const r    = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const arc  = Math.min(Math.max(pct, 0), 100) / 100 * circ;
  return (
    <svg width={size} height={size} style={{ display: 'block', transform: 'rotate(-90deg)' }}>
      <circle cx={size / 2} cy={size / 2} r={r}
        fill="none" stroke="var(--paper-3)" strokeWidth={stroke} />
      <circle cx={size / 2} cy={size / 2} r={r}
        fill="none" stroke="var(--indigo)" strokeWidth={stroke}
        strokeLinecap="round" strokeDasharray={`${arc} ${circ}`} />
    </svg>
  );
}

// ─── Sortable column header ──────────────────────────────────────────────────
function Th({ label, sortKey, active, dir, onSort }) {
  return (
    <div
      style={{ display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer', userSelect: 'none' }}
      onClick={() => onSort(sortKey)}
    >
      {label}
      <Icon
        name={active ? (dir === 'asc' ? 'chevron-up' : 'chevron-down') : 'chevrons-up-down'}
        size={12}
        color="var(--ink-4)"
      />
    </div>
  );
}

// ─── Inline progress bar ─────────────────────────────────────────────────────
function MiniBar({ pct }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{ flex: 1, height: 6, background: 'var(--paper-3)', borderRadius: 999 }}>
        <div style={{
          height: '100%', width: `${pct}%`, borderRadius: 999,
          background: pct === 100 ? 'var(--success)' : 'var(--indigo)',
        }} />
      </div>
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-3)', minWidth: 34, textAlign: 'right' }}>
        {pct.toFixed(0)}%
      </span>
    </div>
  );
}

// ─── Date formatter ──────────────────────────────────────────────────────────
const fmt = (iso) => iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

// ─── Sort helpers ────────────────────────────────────────────────────────────
const NUMERIC_KEYS = new Set(['progressPct', 'lessonsCompleted', 'enrolledAt', 'completedAt']);

function cmpValue(row, key) {
  if (key === 'enrolledAt' || key === 'completedAt') return row[key] ? new Date(row[key]).getTime() : 0;
  if (NUMERIC_KEYS.has(key)) return row[key] ?? 0;
  return (row[key] ?? '').toString().toLowerCase();
}

function sortRows(rows, key, dir) {
  return [...rows].sort((a, b) => {
    const va = cmpValue(a, key), vb = cmpValue(b, key);
    const cmp = typeof va === 'string' ? va.localeCompare(vb) : va - vb;
    return dir === 'asc' ? cmp : -cmp;
  });
}

// ─── Column definitions ──────────────────────────────────────────────────────
const COLS = [
  { key: 'fullName',        label: 'Learner',      width: '1.4fr' },
  { key: 'enrolmentStatus', label: 'Status',        width: '100px' },
  { key: 'progressPct',     label: 'Progress',      width: '1.6fr' },
  { key: 'lessonsCompleted',label: 'Lessons done',  width: '100px' },
  { key: 'enrolledAt',      label: 'Enrolled',      width: '110px' },
  { key: 'completedAt',     label: 'Completed',     width: '110px' },
];

const GRID = COLS.map(c => c.width).join(' ');

// ─── Main page ───────────────────────────────────────────────────────────────
export default function CourseAnalytics() {
  const { id } = useParams();

  const [data,    setData]    = useState(null);
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(null);
  const [sortKey, setSortKey] = useState('enrolledAt');
  const [sortDir, setSortDir] = useState('desc');

  useEffect(() => {
    setLoading(true);
    setError(null);
    const req = id
      ? courseService.analytics(id).then(result => {
          setData(result);
          setCourses([]);
        })
      : courseService.listOwn().then(page => {
          setData(null);
          setCourses(page.content ?? []);
        });

    req
      .catch(e => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  }, [id]);

  const handleSort = (key) => {
    if (sortKey === key) setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    else { setSortKey(key); setSortDir('asc'); }
  };

  const agg     = data?.aggregate ?? {};
  const learners = data?.learners  ?? [];
  const sorted  = sortRows(learners, sortKey, sortDir);
  const rate    = agg.completionRate ?? 0;

  if (!id) {
    return (
      <div className="main">
        <h1 className="page-title">Course analytics</h1>
        <p className="page-lede" style={{ marginBottom: 28 }}>
          Choose a course to view enrolment and completion data.
        </p>

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

        <div className="table-wrap">
          <div className="table-row head" style={{ gridTemplateColumns: '1.8fr 120px 120px 120px' }}>
            <div>Course</div><div>Status</div><div>Visibility</div><div>Action</div>
          </div>

          {loading && <SkeletonTableRows cols="1.8fr 120px 120px 120px" widths={['70%','55%','55%','70%']} count={4} />}

          {!loading && !error && courses.length === 0 && (
            <div style={{ gridColumn: '1 / -1', padding: '40px 24px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 14 }}>
              No courses available for analytics yet.
            </div>
          )}

          {!loading && courses.map(course => (
            <div key={course.id} className="table-row body" style={{ gridTemplateColumns: '1.8fr 120px 120px 120px' }}>
              <div className="row-title">{course.title}</div>
              <div>{course.status === 'PUBLISHED' ? <Tag variant="published">Published</Tag> : <Tag variant="draft">Draft</Tag>}</div>
              <div>{course.visibility === 'PRIVATE' ? <Tag variant="private">Private</Tag> : <Tag variant="public">Public</Tag>}</div>
              <div>
                <Link className="btn btn-secondary btn-xs" to={`/teach/courses/${course.id}/analytics`}>
                  <Icon name="bar-chart-2" size={12} /> View
                </Link>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  const kpis = [
    { label: 'Enrolments',      value: agg.enrolments    != null ? String(agg.enrolments)    : '—' },
    { label: 'Completions',     value: agg.completions   != null ? String(agg.completions)   : '—' },
    { label: 'Completion rate', value: agg.completionRate != null ? `${rate}%`               : '—' },
    { label: 'Active learners', value: agg.active        != null ? String(agg.active)        : '—' },
  ];

  return (
    <div className="main">
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <Link to="/teach/courses" style={{ color: 'var(--ink-4)', fontSize: 13, textDecoration: 'none' }}>
          My courses
        </Link>
        <Icon name="chevron-right" size={12} color="var(--ink-5)" />
        <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>Analytics</span>
      </div>

      <h1 className="page-title" style={{ marginBottom: 6 }}>Course analytics</h1>
      <p className="page-lede" style={{ marginBottom: 28 }}>
        Enrolment and completion data for this course.
      </p>

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

      {/* KPI row */}
      <div className="kpi-row">
        {kpis.map(s => <KPICard key={s.label} {...s} />)}
      </div>

      {/* Completion donut card */}
      <div style={{
        background: '#fff', border: '1px solid var(--rule)', borderRadius: 12,
        padding: '22px 28px', marginBottom: 28,
        display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 32, alignItems: 'center',
      }}>
        {/* Left: donut + label */}
        <div style={{ position: 'relative', width: 140, height: 140 }}>
          <DonutChart pct={rate} />
          <div style={{
            position: 'absolute', inset: 0, display: 'flex',
            flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          }}>
            <span style={{ fontFamily: 'var(--font-display)', fontSize: 28, fontWeight: 600, lineHeight: 1 }}>
              {loading ? '—' : `${rate}%`}
            </span>
            <span style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>complete</span>
          </div>
        </div>

        {/* Right: breakdown */}
        <div>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 20, fontWeight: 500, letterSpacing: '-0.01em', marginBottom: 16 }}>
            Completion rate
          </div>
          <div style={{ display: 'grid', gap: 12 }}>
            {[
              { label: 'Total enrolments', value: agg.enrolments, color: 'var(--indigo-200)' },
              { label: 'Completed',        value: agg.completions, color: 'var(--success)'   },
              { label: 'Still active',     value: agg.active,      color: 'var(--ink-4)'     },
            ].map(({ label, value, color }) => (
              <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ width: 10, height: 10, borderRadius: 2, background: color, flexShrink: 0 }} />
                <span style={{ fontSize: 14, color: 'var(--ink-2)', flex: 1 }}>{label}</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--ink)', fontWeight: 500 }}>
                  {loading ? '—' : (value ?? '—')}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Learner table */}
      <h2 className="section-head" style={{ marginTop: 0 }}>Learner progress</h2>

      <div className="table-wrap analytics-table">
        {/* Header */}
        <div className="table-row head" style={{ gridTemplateColumns: GRID }}>
          {COLS.map(col => (
            <Th
              key={col.key}
              label={col.label}
              sortKey={col.key}
              active={sortKey === col.key}
              dir={sortDir}
              onSort={handleSort}
            />
          ))}
        </div>

        {/* Loading skeleton */}
        {loading && <SkeletonTableRows cols={GRID} widths={['65%','55%','80%','50%','60%','55%']} count={3} />}

        {/* Empty state */}
        {!loading && !error && sorted.length === 0 && (
          <div style={{
            gridColumn: '1 / -1', padding: '40px 24px',
            textAlign: 'center', color: 'var(--ink-4)', fontSize: 14,
          }}>
            No learners enrolled yet.
          </div>
        )}

        {/* Data rows */}
        {!loading && sorted.map(row => (
          <div key={row.userId} className="table-row body" style={{ gridTemplateColumns: GRID }}>
            <div style={{ fontSize: 14, color: 'var(--ink-2)', fontWeight: 500 }}>
              {row.fullName ?? `${row.userId.slice(0, 8)}…`}
            </div>
            <div>
              {row.enrolmentStatus === 'COMPLETED'
                ? <Tag variant="completed">Completed</Tag>
                : <Tag variant="in-progress">In progress</Tag>}
            </div>
            <MiniBar pct={row.progressPct ?? 0} />
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--ink-3)' }}>
              {row.lessonsCompleted}
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{fmt(row.enrolledAt)}</div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{fmt(row.completedAt)}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
