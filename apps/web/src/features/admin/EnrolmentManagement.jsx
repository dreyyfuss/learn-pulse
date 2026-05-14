import { useState, useEffect } from 'react';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Notification from '../../components/Notification';
import Pagination from '../../components/Pagination';
import adminService from '../../services/adminService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonTableRows } from '../../components/Skeleton';

const fmt = (iso) => iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
const lower = (s) => (s ?? '').toLowerCase();
const GRID = '1.2fr 1.8fr 100px 120px 80px';

export default function EnrolmentManagement() {
  const [enrolments, setEnrolments] = useState([]);
  const [learners, setLearners]     = useState([]);
  const [courses, setCourses]       = useState([]);
  const [loading, setLoading]       = useState(true);
  const [tableLoading, setTableLoading] = useState(true);
  const [error, setError]           = useState(null);
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [refreshKey, setRefreshKey] = useState(0);

  const [learnerSearch, setLearnerSearch] = useState('');
  const [courseSearch, setCourseSearch]   = useState('');
  const [selectedLearner, setSelectedLearner] = useState(null);
  const [selectedCourse, setSelectedCourse]   = useState(null);
  const [showLearnerDrop, setShowLearnerDrop] = useState(false);
  const [showCourseDrop, setShowCourseDrop]   = useState(false);
  const [enrolling, setEnrolling]   = useState(false);
  const [toast, setToast]           = useState('');

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

  useEffect(() => {
    setLoading(true);
    Promise.all([
      adminService.getUsers({ role: 'LEARNER', size: 100 }).then(p => (p.content ?? []).map(u => ({ ...u, name: u.fullName }))),
      adminService.getCourses({ size: 100 }).then(p => p.content ?? []),
    ])
      .then(([lrns, crs]) => { setLearners(lrns); setCourses(crs); })
      .catch(e => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    setTableLoading(true);
    adminService.getEnrolments({ size: 20, page })
      .then(data => {
        setEnrolments(data.content ?? []);
        setTotalPages(data.totalPages ?? 1);
      })
      .catch(e => setError(getErrorMessage(e)))
      .finally(() => setTableLoading(false));
  }, [page, refreshKey]);

  const filteredLearners = learners.filter(u =>
    !learnerSearch || u.name?.toLowerCase().includes(learnerSearch.toLowerCase()) || u.email?.toLowerCase().includes(learnerSearch.toLowerCase())
  );
  const filteredCourses = courses.filter(c =>
    !courseSearch || c.title?.toLowerCase().includes(courseSearch.toLowerCase())
  );

  const handleEnrol = () => {
    if (!selectedLearner || !selectedCourse) return;
    setEnrolling(true);
    adminService.enrol(selectedLearner.id, selectedCourse.id)
      .then(() => {
        showToast(`${selectedLearner.name} enrolled in ${selectedCourse.title}.`);
        setSelectedLearner(null); setSelectedCourse(null); setLearnerSearch(''); setCourseSearch('');
        setPage(0);
        setRefreshKey(k => k + 1);
      })
      .catch(e => showToast(getErrorMessage(e)))
      .finally(() => setEnrolling(false));
  };

  const handleUnenrol = (enrolmentId) => {
    adminService.unenrol(enrolmentId)
      .then(() => { setEnrolments(es => es.filter(e => e.enrolmentId !== enrolmentId)); showToast('Learner unenrolled.'); })
      .catch(e => showToast(getErrorMessage(e)));
  };

  const anyLoading = loading || tableLoading;

  return (
    <div className="main">
      <div className="page-eyebrow">Admin · Enrolments</div>
      <h1 className="page-title">Enrolment management</h1>
      <p className="page-lede">Manually enrol or unenrol a learner from any course.</p>

      {error && (
        <div style={{ background: 'var(--danger-bg)', border: '1px solid var(--coral-200)', color: 'var(--danger)', borderRadius: 8, padding: '12px 16px', marginBottom: 16, fontSize: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="alert-circle" size={16} color="var(--danger)" />{error}
        </div>
      )}

      {/* Enrol form */}
      <div style={{ background: '#fff', border: '1px solid var(--rule)', borderRadius: 12, padding: '24px 28px', marginBottom: 32 }}>
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 500, margin: '0 0 18px' }}>Enrol a learner</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 14, alignItems: 'end' }}>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Learner</label>
            <div className="search-wrap" style={{ width: '100%' }}>
              <input className="input" placeholder="Search by name or email…"
                value={selectedLearner ? selectedLearner.name : learnerSearch}
                onChange={e => { setLearnerSearch(e.target.value); setSelectedLearner(null); setShowLearnerDrop(true); }}
                onFocus={() => setShowLearnerDrop(true)}
                onBlur={() => setTimeout(() => setShowLearnerDrop(false), 150)}
              />
              {showLearnerDrop && filteredLearners.length > 0 && (
                <div className="search-dropdown">
                  {filteredLearners.map(u => (
                    <div key={u.id} className="search-item"
                      onMouseDown={() => { setSelectedLearner(u); setLearnerSearch(u.name); setShowLearnerDrop(false); }}>
                      <strong>{u.name}</strong> <span style={{ color: 'var(--ink-3)' }}>{u.email}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Course</label>
            <div className="search-wrap" style={{ width: '100%' }}>
              <input className="input" placeholder="Search courses…"
                value={selectedCourse ? selectedCourse.title : courseSearch}
                onChange={e => { setCourseSearch(e.target.value); setSelectedCourse(null); setShowCourseDrop(true); }}
                onFocus={() => setShowCourseDrop(true)}
                onBlur={() => setTimeout(() => setShowCourseDrop(false), 150)}
              />
              {showCourseDrop && filteredCourses.length > 0 && (
                <div className="search-dropdown">
                  {filteredCourses.map(c => (
                    <div key={c.id} className="search-item"
                      onMouseDown={() => { setSelectedCourse(c); setCourseSearch(c.title); setShowCourseDrop(false); }}>
                      <strong>{c.title}</strong>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <button className="btn btn-primary" disabled={!selectedLearner || !selectedCourse || enrolling} onClick={handleEnrol}>
            <Icon name="user-plus" size={15} /> Enrol
          </button>
        </div>
      </div>

      {/* Enrolments table */}
      <h2 className="section-head" style={{ marginTop: 0 }}>Recent enrolments</h2>
      <div className="table-wrap">
        <div className="table-row head" style={{ gridTemplateColumns: GRID }}>
          <div>Learner</div><div>Course</div><div>Status</div><div>Enrolled</div><div>Action</div>
        </div>

        {anyLoading && <SkeletonTableRows cols={GRID} widths={['70%','70%','55%','60%','40%']} count={3} />}

        {!anyLoading && !error && enrolments.length === 0 && (
          <div style={{ gridColumn: '1 / -1', padding: '40px 24px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 14 }}>
            No enrolments found.
          </div>
        )}

        {!anyLoading && enrolments.map(e => (
          <div key={e.enrolmentId} className="table-row body" style={{ gridTemplateColumns: GRID }}>
            <div>
              <div style={{ fontWeight: 500, fontSize: 14 }}>
                {e._learnerName
                  ? e._learnerName
                  : <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-3)' }}>{e.userId?.slice(0, 8)}…</span>
                }
              </div>
              {e._learnerEmail && <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{e._learnerEmail}</div>}
            </div>
            <div style={{ fontSize: 14, color: 'var(--ink-2)' }}>{e.courseTitle}</div>
            <div>
              <Tag variant={lower(e.status) === 'completed' ? 'completed' : 'in-progress'}>
                {lower(e.status) === 'completed' ? 'Completed' : 'In progress'}
              </Tag>
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{fmt(e.enrolledAt)}</div>
            <div>
              <button className="btn btn-danger btn-xs" onClick={() => handleUnenrol(e.enrolmentId)}>Unenrol</button>
            </div>
          </div>
        ))}
      </div>

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}