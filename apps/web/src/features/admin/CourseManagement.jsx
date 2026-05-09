import { useState } from 'react';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import { ADMIN_COURSES } from '../../data/mockData';

export default function CourseManagement() {
  const [courses, setCourses] = useState(ADMIN_COURSES);
  const [deleteModal, setDeleteModal] = useState(null);
  const [toast, setToast] = useState('');

  const handleDelete = (courseId) => {
    setCourses(cs => cs.filter(c => c.id !== courseId));
    setDeleteModal(null);
    setToast('Course deleted. All associated enrolments and completions removed.');
    setTimeout(() => setToast(''), 4000);
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Admin · Courses</div>
      <h1 className="page-title">Course management</h1>
      <div className="table-wrap admin-courses-table" style={{ marginTop: 20 }}>
        <div className="table-row head" style={{ gridTemplateColumns: '1.8fr 1.2fr 90px 90px 80px 80px 70px' }}>
          <div>Title</div><div>Instructor</div><div>Visibility</div><div>Status</div><div>Enrolled</div><div>Completions</div><div>Action</div>
        </div>
        {courses.map(c => (
          <div key={c.id} className="table-row body" style={{ gridTemplateColumns: '1.8fr 1.2fr 90px 90px 80px 80px 70px' }}>
            <div><div className="row-title">{c.title}</div></div>
            <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>{c.instructor}</div>
            <div><Tag variant={c.visibility === 'private' ? 'private' : 'public'}>{c.visibility.charAt(0).toUpperCase() + c.visibility.slice(1)}</Tag></div>
            <div>
              {c.status === 'published' ? <Tag variant="published">Published</Tag>
               : c.status === 'locked' ? <Tag variant="locked">Locked</Tag>
               : <Tag variant="draft">Draft</Tag>}
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{c.enrolled}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13 }}>{c.completions}</div>
            <div><button className="btn btn-danger btn-xs" onClick={() => setDeleteModal(c)}>Delete</button></div>
          </div>
        ))}
      </div>
      {deleteModal && (
        <Modal title="Delete course?" onClose={() => setDeleteModal(null)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setDeleteModal(null)}>Cancel</button>
              <button className="btn btn-danger" onClick={() => handleDelete(deleteModal.id)}>Delete permanently</button>
            </>
          }
        >
          <p>You're about to permanently delete <strong>{deleteModal.title}</strong>. This will also remove all <strong>{deleteModal.enrolled} enrolments</strong>, completion records, and issued certificates. This cannot be undone.</p>
        </Modal>
      )}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
