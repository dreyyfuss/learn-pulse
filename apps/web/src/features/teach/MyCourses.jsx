import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import courseService from '../../services/courseService';

const EMPTY_FORM = { title: '', visibility: 'PUBLIC', description: '' };

export default function MyCourses() {
  const navigate = useNavigate();
  const location = useLocation();
  const [courses, setCourses]       = useState([]);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');

  // Create-course modal — auto-open when navigated here with { state: { openCreate: true } }
  const [showCreate, setShowCreate] = useState(!!location.state?.openCreate);
  const [form, setForm]             = useState(EMPTY_FORM);
  const [creating, setCreating]     = useState(false);
  const [createError, setCreateError] = useState('');

  useEffect(() => {
    courseService.listOwn()
      .then(data => setCourses(data.items ?? []))
      .catch(err => setError(err.message || 'Could not load courses.'))
      .finally(() => setLoading(false));
  }, []);

  const fmtDate = (iso) =>
    iso ? new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

  const openCreate = () => { setForm(EMPTY_FORM); setCreateError(''); setShowCreate(true); };
  const closeCreate = () => { if (!creating) setShowCreate(false); };

  const set = (field) => (e) => setForm(prev => ({ ...prev, [field]: e.target.value }));

  const submitCreate = async (e) => {
    e.preventDefault();
    if (!form.title.trim()) { setCreateError('Title is required.'); return; }
    setCreating(true);
    setCreateError('');
    try {
      const data = await courseService.create({
        title:       form.title.trim(),
        visibility:  form.visibility,
        description: form.description.trim() || null,
      });
      setShowCreate(false);
      navigate(`/teach/courses/${data.courseId}/edit`);
    } catch (err) {
      setCreateError(err.message || 'Failed to create course.');
      setCreating(false);
    }
  };

  return (
    <div className="main">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <div className="page-eyebrow">Teaching</div>
          <h1 className="page-title">My courses</h1>
        </div>
        <button
          className="btn btn-primary"
          style={{ marginTop: 8, flexShrink: 0 }}
          onClick={openCreate}
        >
          <Icon name="plus" size={15} /> New course
        </button>
      </div>

      {loading && (
        <p style={{ color: 'var(--ink-3)', textAlign: 'center', padding: '60px 0' }}>Loading…</p>
      )}

      {error && (
        <p style={{ color: 'var(--danger)', textAlign: 'center', padding: '60px 0' }}>{error}</p>
      )}

      {!loading && !error && courses.length === 0 && (
        <div style={{ textAlign: 'center', padding: '80px 0', color: 'var(--ink-3)' }}>
          <Icon name="book-open" size={40} color="var(--ink-4)" />
          <p style={{ marginTop: 16, fontSize: 15 }}>No courses yet — create your first one.</p>
          <button className="btn btn-primary" style={{ marginTop: 12 }} onClick={openCreate}>
            <Icon name="plus" size={14} /> New course
          </button>
        </div>
      )}

      {!loading && !error && courses.length > 0 && (
        <div className="table-wrap">
          <div className="table-row head" style={{ gridTemplateColumns: '1fr 120px 110px 130px 170px' }}>
            <div>Course</div>
            <div>Status</div>
            <div>Visibility</div>
            <div>Created</div>
            <div>Actions</div>
          </div>

          {courses.map(c => (
            <div key={c.id} className="table-row body" style={{ gridTemplateColumns: '1fr 120px 110px 130px 170px' }}>
              <div>
                <div className="row-title">{c.title}</div>
                {c.category && <div className="row-sub">{c.category}</div>}
              </div>
              <div>
                {c.status === 'PUBLISHED'
                  ? <Tag variant="published">Published</Tag>
                  : <Tag variant="draft">Draft</Tag>}
              </div>
              <div>
                {c.visibility === 'PRIVATE'
                  ? <Tag variant="private">Private</Tag>
                  : <Tag variant="public">Public</Tag>}
              </div>
              <div style={{ fontSize: 13, color: 'var(--ink-3)', fontFamily: 'var(--font-mono)' }}>
                {fmtDate(c.createdAt)}
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                <button className="btn btn-secondary btn-xs" onClick={() => navigate(`/teach/courses/${c.id}/edit`)}>
                  <Icon name="pencil" size={12} /> Edit
                </button>
                <button className="btn btn-secondary btn-xs" onClick={() => navigate(`/teach/courses/${c.id}/analytics`)}>
                  <Icon name="bar-chart-2" size={12} /> Analytics
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create-course modal */}
      {showCreate && (
        <Modal
          title="New course"
          onClose={closeCreate}
          actions={
            <>
              <button className="btn btn-secondary" onClick={closeCreate} disabled={creating}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={submitCreate} disabled={creating}>
                {creating ? 'Creating…' : 'Create course'}
              </button>
            </>
          }
        >
          <form onSubmit={submitCreate} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="field">
              <label>Course title <span style={{ color: 'var(--danger)' }}>*</span></label>
              <input
                className="input"
                value={form.title}
                onChange={set('title')}
                placeholder="e.g. Data structures, in plain English"
                maxLength={200}
                autoFocus
                disabled={creating}
              />
            </div>

            <div className="field">
              <label>Visibility</label>
              <select className="input select" value={form.visibility} onChange={set('visibility')} disabled={creating}>
                <option value="PUBLIC">Public — anyone can enrol</option>
                <option value="PRIVATE">Private — enrolment code required</option>
              </select>
            </div>

            <div className="field">
              <label>Description <span style={{ color: 'var(--ink-4)', fontWeight: 400 }}>(optional)</span></label>
              <textarea
                className="input textarea"
                value={form.description}
                onChange={set('description')}
                placeholder="What will learners get out of this course?"
                rows={3}
                disabled={creating}
              />
            </div>

            {createError && (
              <p style={{ color: 'var(--danger)', margin: 0, fontSize: 14 }}>{createError}</p>
            )}
          </form>
        </Modal>
      )}
    </div>
  );
}
