import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import courseService from '../../services/courseService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonCourseCard } from '../../components/Skeleton';

export default function CourseDiscovery() {
  const navigate = useNavigate();
  const [courses, setCourses]       = useState([]);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [filter, setFilter]         = useState('All');
  const [search, setSearch]         = useState('');
  const [requestModal, setRequestModal] = useState(null);
  const [enrollCode, setEnrollCode] = useState('');
  const [toast, setToast]           = useState('');

  useEffect(() => {
    courseService.list({ size: 100 })
      .then(data => {
        const published = (data.items ?? []).filter(c => c.status === 'PUBLISHED');
        setCourses(published);
      })
      .catch(err => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const categories = ['All', ...new Set(courses.map(c => c.category).filter(Boolean))];

  const filtered = courses.filter(c => {
    const matchCat    = filter === 'All' || c.category === filter;
    const q           = search.toLowerCase();
    const matchSearch = !q
      || c.title.toLowerCase().includes(q)
      || (c.description ?? '').toLowerCase().includes(q);
    return matchCat && matchSearch;
  });

  const handleRequest = () => {
    setToast("Access requested. You'll hear back within 24 hours.");
    setRequestModal(null);
    setEnrollCode('');
    setTimeout(() => setToast(''), 3500);
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
            onChange={e => setSearch(e.target.value)}
            placeholder="Search courses…"
            style={{ border: 0, outline: 0, background: 'transparent', font: 'inherit', fontSize: 14, flex: 1, color: 'var(--ink)' }}
          />
        </div>
      </div>

      {/* Category chips — only shown once courses are loaded */}
      {!loading && categories.length > 1 && (
        <div className="filter-row">
          {categories.map(t => (
            <span
              key={t}
              className={`chip${filter === t ? ' active' : ''}`}
              onClick={() => setFilter(t)}
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

      {!loading && !error && filtered.length === 0 && (
        <p style={{ color: 'var(--ink-3)', textAlign: 'center', padding: '60px 0' }}>
          {courses.length === 0 ? 'No published courses yet.' : 'No courses match your search.'}
        </p>
      )}

      {/* Course grid */}
      {!loading && !error && filtered.length > 0 && (
        <div className="course-grid">
          {filtered.map(c => (
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

      {/* Private-course request modal */}
      {requestModal && (
        <Modal
          title="Request access"
          onClose={() => setRequestModal(null)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setRequestModal(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleRequest}>Send request</button>
            </>
          }
        >
          <p>
            Have an enrolment code? Enter it below to get immediate access to{' '}
            <strong>{requestModal.title}</strong>.
            Otherwise, send a request and the instructor will review it.
          </p>
          <div className="field">
            <label>Enrolment code (optional)</label>
            <input
              className="input"
              value={enrollCode}
              onChange={e => setEnrollCode(e.target.value)}
              placeholder="e.g. LEARN-2026-XY"
              style={{ fontFamily: 'var(--font-mono)' }}
            />
          </div>
        </Modal>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
