import { useState, useEffect } from 'react';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import Pagination from '../../components/Pagination';
import adminService from '../../services/adminService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonTableRows } from '../../components/Skeleton';

const lower = (s) => (s ?? '').toLowerCase();
const GRID = '1.8fr 1.2fr 90px 90px 70px';

export default function CourseManagement() {
  const [courses, setCourses]     = useState([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState(null);
  const [search, setSearch]       = useState('');
  const [page, setPage]           = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [deleteModal, setDeleteModal] = useState(null);
  const [toast, setToast]         = useState('');

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 4000); };

  useEffect(() => {
    const t = setTimeout(() => {
      setLoading(true);
      setError(null);
      adminService.getCourses({ size: 20, page, ...(search.trim() ? { q: search.trim() } : {}) })
        .then(data => {
          setCourses(data.content ?? []);
          setTotalPages(data.totalPages ?? 1);
        })
        .catch(e => setError(getErrorMessage(e)))
        .finally(() => setLoading(false));
    }, search ? 350 : 0);
    return () => clearTimeout(t);
  }, [search, page]);

  const handleDelete = (courseId) => {
    adminService.deleteCourse(courseId)
      .then(() => {
        setCourses(cs => cs.filter(c => c.id !== courseId));
        showToast('Course deleted. All associated enrolments and completions removed.');
      })
      .catch(e => showToast(getErrorMessage(e)))
      .finally(() => setDeleteModal(null));
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Admin · Courses</div>
      <h1 className="page-title">Course management</h1>

      {error && (
        <div style={{ background: 'var(--danger-bg)', border: '1px solid var(--coral-200)', color: 'var(--danger)', borderRadius: 8, padding: '12px 16px', marginBottom: 16, fontSize: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="alert-circle" size={16} color="var(--danger)" />{error}
        </div>
      )}

      <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid var(--rule)', borderRadius: 8, padding: '8px 14px', maxWidth: 360, marginBottom: 20 }}>
        <Icon name="search" size={15} color="var(--ink-3)" />
        <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} placeholder="Search courses…"
          style={{ border: 0, outline: 0, background: 'transparent', font: 'inherit', fontSize: 14, flex: 1, color: 'var(--ink)' }} />
      </div>

      <div className="table-wrap admin-courses-table">
        <div className="table-row head" style={{ gridTemplateColumns: GRID }}>
          <div>Title</div><div>Instructor</div><div>Visibility</div><div>Status</div><div>Action</div>
        </div>

        {loading && <SkeletonTableRows cols={GRID} widths={['70%','60%','55%','55%','40%']} count={3} />}

        {!loading && courses.map(c => (
          <div key={c.id} className="table-row body" style={{ gridTemplateColumns: GRID }}>
            <div><div className="row-title">{c.title}</div></div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-3)' }}>
              {c.instructorId?.slice(0, 8)}…
            </div>
            <div>
              <Tag variant={lower(c.visibility) === 'private' ? 'private' : 'public'}>
                {lower(c.visibility).charAt(0).toUpperCase() + lower(c.visibility).slice(1)}
              </Tag>
            </div>
            <div>
              {lower(c.status) === 'published'
                ? <Tag variant="published">Published</Tag>
                : <Tag variant="draft">Draft</Tag>}
            </div>
            <div>
              <button className="btn btn-danger btn-xs" onClick={() => setDeleteModal(c)}>Delete</button>
            </div>
          </div>
        ))}

        {!loading && !error && courses.length === 0 && (
          <div style={{ gridColumn: '1 / -1', padding: '40px 24px', textAlign: 'center', color: 'var(--ink-4)', fontSize: 14 }}>
            No courses found.
          </div>
        )}
      </div>

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {deleteModal && (
        <Modal title="Delete course?" onClose={() => setDeleteModal(null)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setDeleteModal(null)}>Cancel</button>
              <button className="btn btn-danger" onClick={() => handleDelete(deleteModal.id)}>Delete permanently</button>
            </>
          }
        >
          <p>You're about to permanently delete <strong>{deleteModal.title}</strong>. This will remove all associated enrolments, completion records, and issued certificates. This cannot be undone.</p>
        </Modal>
      )}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}