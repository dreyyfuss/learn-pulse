import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import Pagination from '../../components/Pagination';
import courseService from '../../services/courseService';
import enrolmentService from '../../services/enrolmentService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonCourseCard } from '../../components/Skeleton';

export default function CourseDiscovery() {
  const navigate = useNavigate();
  const [courses, setCourses]       = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [filter, setFilter]         = useState('All');
  const [search, setSearch]         = useState('');
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [requestModal, setRequestModal] = useState(null);
  const [enrollCode, setEnrollCode] = useState('');
  const [codeError, setCodeError]   = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast]           = useState('');

  useEffect(() => {
    courseService.list({ size: 200 })
      .then(data => {
        const cats = [...new Set((data.items ?? []).map(c => c.category).filter(Boolean))];
        setCategories(cats);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const t = setTimeout(() => {
      setLoading(true);
      setError('');
      const params = {
        size: 12,
        page,
        ...(search.trim() ? { q: search.trim() } : {}),
        ...(filter !== 'All' ? { category: filter } : {}),
      };
      courseService.list(params)
        .then(data => {
          setCourses(data.items ?? []);
          setTotalPages(data.totalPages ?? 1);
        })
        .catch(err => setError(getErrorMessage(err)))
        .finally(() => setLoading(false));
    }, search ? 350 : 0);
    return () => clearTimeout(t);
  }, [search, filter, page]);

  const handleRequest = async () => {
    if (!enrollCode.trim()) { setCodeError('Please enter an enrolment code.'); return; }
    setCodeError('');
    setSubmitting(true);
    try {
      await enrolmentService.enrol(requestModal.id, enrollCode.trim());
      setRequestModal(null);
      setEnrollCode('');
      navigate(`/learn/courses/${requestModal.id}`);
    } catch (err) {
      setCodeError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Course catalogue</div>
      <h1 className="page-title">Find your next course.</h1>
      <p className="page-lede">Short, well-paced, made by people who've done the work.</p>

      {/* Search bar */}
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 20 }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8,
          background: '#fff', border: '1px solid var(--rule)',
          borderRadius: 8, padding: '8px 14px', flex: 1, maxWidth: 360,
        }}>
          <Icon name="search" size={15} color="var(--ink-3)" />
          <input
            value={search}
            onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Search courses…"
            style={{ border: 0, outline: 0, background: 'transparent', font: 'inherit', fontSize: 14, flex: 1, color: 'var(--ink)' }}
          />
        </div>
      </div>

      {/* Category chips */}
      {categories.length > 0 && (
        <div className="filter-row">
          {['All', ...categories].map(t => (
            <span
              key={t}
              className={`chip${filter === t ? ' active' : ''}`}
              onClick={() => { setFilter(t); setPage(0); }}
            >
              {t}
            </span>
          ))}
        </div>
      )}

      {/* States */}
      {loading && (
        <div className="course-grid">{[0,1,2].map(i => <SkeletonCourseCard key={i} />)}</div>
      )}

      {error && (
        <p style={{ color: 'var(--danger)', textAlign: 'center', padding: '60px 0' }}>{error}</p>
      )}

      {!loading && !error && courses.length === 0 && (
        <p style={{ color: 'var(--ink-3)', textAlign: 'center', padding: '60px 0' }}>
          {!search && filter === 'All' ? 'No published courses yet.' : 'No courses match your search.'}
        </p>
      )}

      {/* Course grid */}
      {!loading && !error && courses.length > 0 && (
        <div className="course-grid">
          {courses.map(c => (
            <div key={c.id} className="course-card" onClick={() => navigate(`/learn/courses/${c.id}`)}>
              <div className="thumb">
                <Icon name="book-open" size={32} className="thumb-icon" />
                {c.visibility === 'PRIVATE' && (
                  <span style={{
                    position: 'absolute', top: 10, right: 10,
                    background: 'rgba(0,0,0,.5)', color: '#fff',
                    fontSize: 11, padding: '3px 8px', borderRadius: 999,
                    display: 'flex', alignItems: 'center', gap: 4,
                  }}>
                    <Icon name="lock" size={11} color="#fff" /> Private
                  </span>
                )}
              </div>

              <div className="card-eyebrow">{c.category || 'General'}</div>
              <h3>{c.title}</h3>
              <p style={{ fontSize: 13, color: 'var(--ink-3)', margin: 0, lineHeight: 1.5 }}>
                {c.description}
              </p>
              <div className="card-meta">Published course</div>

              <div className="card-footer">
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <Tag variant={c.visibility === 'PRIVATE' ? 'private' : 'public'}>
                    {c.visibility === 'PRIVATE' ? 'Private' : 'Public'}
                  </Tag>
                </div>
                <button
                  className={`btn btn-sm ${c.visibility === 'PRIVATE' ? 'btn-secondary' : 'btn-primary'}`}
                  style={{ alignSelf: 'flex-start', marginTop: 8 }}
                  onClick={e => {
                    e.stopPropagation();
                    c.visibility === 'PRIVATE'
                      ? setRequestModal(c)
                      : navigate(`/learn/courses/${c.id}`);
                  }}
                >
                  {c.visibility === 'PRIVATE' ? 'Request access' : 'View course'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {/* Private-course request modal */}
      {requestModal && (
        <Modal
          title="Enter enrolment code"
          onClose={() => { setRequestModal(null); setEnrollCode(''); setCodeError(''); }}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => { setRequestModal(null); setEnrollCode(''); setCodeError(''); }}>Cancel</button>
              <button className="btn btn-primary" onClick={handleRequest} disabled={submitting}>
                {submitting ? 'Enrolling…' : 'Enrol'}
              </button>
            </>
          }
        >
          <p>
            Enter the enrolment code to get immediate access to{' '}
            <strong>{requestModal.title}</strong>.
          </p>
          <div className="field">
            <label>Enrolment code</label>
            <input
              className="input"
              value={enrollCode}
              onChange={e => { setEnrollCode(e.target.value); setCodeError(''); }}
              placeholder="e.g. LEARN-2026-XY"
              style={{ fontFamily: 'var(--font-mono)' }}
              autoFocus
            />
            {codeError && <p style={{ fontSize: 13, color: 'var(--danger)', marginTop: 6, marginBottom: 0 }}>{codeError}</p>}
          </div>
        </Modal>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}