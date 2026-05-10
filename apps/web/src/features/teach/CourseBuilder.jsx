import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Navigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import courseService from '../../services/courseService';


// ─── Edit-course builder ─────────────────────────────────────────────────────

function EditCourseBuilder({ courseId }) {
  const navigate = useNavigate();

  // Remote state
  const [course, setCourse]   = useState(null);
  const [modules, setModules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  // Editor state
  const [courseTitle, setCourseTitle] = useState('');
  const [activeLesson, setActiveLesson] = useState(null); // { lesson, moduleId }
  const [lessonForm, setLessonForm] = useState({ title: '', contentType: 'VIDEO', contentUrl: '', description: '' });

  // Inline-add state
  const [addingModule, setAddingModule] = useState(false);
  const [newModuleTitle, setNewModuleTitle] = useState('');
  const [addingLessonModuleId, setAddingLessonModuleId] = useState(null);
  const [newLessonTitle, setNewLessonTitle] = useState('');

  // UI state
  const [saving, setSaving] = useState(false);
  const [showPublish, setShowPublish] = useState(false);
  const [publishError, setPublishError] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [toast, setToast] = useState('');

  // DnD refs
  const dragModuleIdx = useRef(null);
  const dragLesson    = useRef({ moduleId: null, idx: null });

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

  // ── Load course ──
  useEffect(() => {
    courseService.get(courseId)
      .then(data => {
        setCourse(data);
        setCourseTitle(data.title);
        setModules(data.modules ?? []);
      })
      .catch(err => setLoadError(err.message || 'Could not load course.'))
      .finally(() => setLoading(false));
  }, [courseId]);

  const isLocked    = course?.isLocked;
  const isPublished = course?.status === 'PUBLISHED';

  // ── Save course title ──
  const saveDraft = async () => {
    if (!courseTitle.trim()) return;
    setSaving(true);
    try {
      const updated = await courseService.update(courseId, { title: courseTitle.trim() });
      setCourse(prev => ({ ...prev, ...updated }));
      showToast('Draft saved.');
    } catch (err) {
      showToast('Save failed: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  // ── Publish ──
  const publish = async () => {
    setPublishing(true);
    setPublishError('');
    try {
      const updated = await courseService.publish(courseId);
      setCourse(prev => ({ ...prev, ...updated }));
      setShowPublish(false);
      showToast('Course published.');
    } catch (err) {
      if (err.status === 422) {
        setPublishError(err.message || 'Each module must contain at least one lesson.');
      } else {
        setPublishError(err.message || 'Publish failed.');
      }
    } finally {
      setPublishing(false);
    }
  };

  // ── Add module ──
  const submitAddModule = async (e) => {
    e?.preventDefault();
    if (!newModuleTitle.trim()) return;
    try {
      const mod = await courseService.createModule(courseId, {
        title: newModuleTitle.trim(),
        orderIndex: modules.length,
      });
      setModules(prev => [...prev, mod]);
      setNewModuleTitle('');
      setAddingModule(false);
    } catch (err) {
      showToast('Failed to add module: ' + err.message);
    }
  };

  // ── Delete module ──
  const deleteModule = async (moduleId) => {
    try {
      await courseService.deleteModule(courseId, moduleId);
      setModules(prev => prev.filter(m => m.id !== moduleId));
      if (activeLesson?.moduleId === moduleId) setActiveLesson(null);
    } catch (err) {
      showToast('Failed to delete module: ' + err.message);
    }
  };

  // ── Add lesson ──
  const submitAddLesson = async (e, moduleId) => {
    e?.preventDefault();
    if (!newLessonTitle.trim()) return;
    const mod = modules.find(m => m.id === moduleId);
    try {
      const lesson = await courseService.createLesson(courseId, moduleId, {
        title: newLessonTitle.trim(),
        contentType: 'VIDEO',
        orderIndex: mod ? mod.lessons.length : 0,
      });
      setModules(prev => prev.map(m =>
        m.id === moduleId ? { ...m, lessons: [...(m.lessons ?? []), lesson] } : m
      ));
      setNewLessonTitle('');
      setAddingLessonModuleId(null);
    } catch (err) {
      showToast('Failed to add lesson: ' + err.message);
    }
  };

  // ── Delete lesson ──
  const deleteLesson = async (moduleId, lessonId) => {
    try {
      await courseService.deleteLesson(courseId, moduleId, lessonId);
      setModules(prev => prev.map(m =>
        m.id === moduleId ? { ...m, lessons: m.lessons.filter(l => l.id !== lessonId) } : m
      ));
      if (activeLesson?.lesson?.id === lessonId) setActiveLesson(null);
    } catch (err) {
      showToast('Failed to delete lesson: ' + err.message);
    }
  };

  // ── Save lesson ──
  const saveLesson = async () => {
    if (!activeLesson) return;
    const { lesson, moduleId } = activeLesson;
    setSaving(true);
    try {
      const body = {
        title:       lessonForm.title       || null,
        contentType: lessonForm.contentType || null,
        contentUrl:  lessonForm.contentUrl  || null,
        description: lessonForm.description || null,
      };
      const updated = await courseService.updateLesson(courseId, moduleId, lesson.id, body);
      setModules(prev => prev.map(m =>
        m.id === moduleId
          ? { ...m, lessons: m.lessons.map(l => l.id === lesson.id ? { ...l, ...updated } : l) }
          : m
      ));
      setActiveLesson({ lesson: { ...lesson, ...updated }, moduleId });
      showToast('Lesson saved.');
    } catch (err) {
      showToast('Save failed: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  // ── DnD — modules ──
  const onModuleDragStart = (idx) => { dragModuleIdx.current = idx; };
  const onModuleDragOver  = (e)   => e.preventDefault();
  const onModuleDrop = async (targetIdx) => {
    const fromIdx = dragModuleIdx.current;
    if (fromIdx === null || fromIdx === targetIdx) return;
    const reordered = [...modules];
    const [moved] = reordered.splice(fromIdx, 1);
    reordered.splice(targetIdx, 0, moved);
    const ordered = reordered.map((m, i) => ({ ...m, orderIndex: i }));
    setModules(ordered);
    dragModuleIdx.current = null;
    try {
      await Promise.all(
        ordered.map(m => courseService.updateModule(courseId, m.id, { orderIndex: m.orderIndex }))
      );
    } catch {
      showToast('Failed to save module order.');
    }
  };

  // ── DnD — lessons ──
  const onLessonDragStart = (moduleId, idx) => { dragLesson.current = { moduleId, idx }; };
  const onLessonDragOver  = (e) => e.preventDefault();
  const onLessonDrop = async (targetModuleId, targetIdx) => {
    const { moduleId: fromModuleId, idx: fromIdx } = dragLesson.current;
    if (fromModuleId !== targetModuleId || fromIdx === targetIdx) return;
    const mIdx = modules.findIndex(m => m.id === targetModuleId);
    const lessons = [...modules[mIdx].lessons];
    const [moved] = lessons.splice(fromIdx, 1);
    lessons.splice(targetIdx, 0, moved);
    const ordered = lessons.map((l, i) => ({ ...l, orderIndex: i }));
    setModules(prev => prev.map((m, i) => i === mIdx ? { ...m, lessons: ordered } : m));
    dragLesson.current = { moduleId: null, idx: null };
    try {
      await Promise.all(
        ordered.map(l => courseService.updateLesson(courseId, targetModuleId, l.id, { orderIndex: l.orderIndex }))
      );
    } catch {
      showToast('Failed to save lesson order.');
    }
  };

  // ── Select lesson ──
  const selectLesson = (lesson, moduleId) => {
    setActiveLesson({ lesson, moduleId });
    setLessonForm({
      title:       lesson.title       ?? '',
      contentType: lesson.contentType ?? 'VIDEO',
      contentUrl:  lesson.contentUrl  ?? '',
      description: lesson.description ?? '',
    });
  };

  // ── Render states ──
  if (loading) return (
    <div style={{ display: 'flex', height: 'calc(100vh - 60px)', alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ color: 'var(--ink-3)' }}>Loading course…</p>
    </div>
  );

  if (loadError) return (
    <div style={{ display: 'flex', height: 'calc(100vh - 60px)', alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ color: 'var(--danger)' }}>{loadError}</p>
    </div>
  );

  const statusVariant = isLocked ? 'locked' : isPublished ? 'published' : 'draft';
  const statusLabel   = isLocked ? 'Locked'  : isPublished ? 'Published' : 'Draft';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 60px)', width: '100%' }}>

      {/* Top bar */}
      <div className="builder-topbar">
        {isLocked
          ? <span style={{ fontFamily: 'var(--font-display)', fontSize: 22, fontWeight: 500, color: 'var(--ink)', flex: 1 }}>{courseTitle}</span>
          : <input
              className="course-title-edit"
              value={courseTitle}
              onChange={e => setCourseTitle(e.target.value)}
              onBlur={() => courseTitle !== course?.title && saveDraft()}
            />
        }
        <Tag variant={statusVariant}>{statusLabel}</Tag>
        {!isLocked && !isPublished && (
          <>
            <button className="btn btn-secondary btn-sm" onClick={saveDraft} disabled={saving}>
              {saving ? 'Saving…' : 'Save draft'}
            </button>
            <button
              className="btn btn-primary btn-sm"
              onClick={() => { setPublishError(''); setShowPublish(true); }}
            >
              Publish
            </button>
          </>
        )}
        <button className="btn btn-secondary btn-sm" onClick={() => navigate('/teach/courses')}>
          ← My courses
        </button>
      </div>

      {/* Builder shell */}
      <div className="builder-shell" style={{ flex: 1, overflow: 'hidden' }}>

        {/* Left — module tree */}
        <div className="builder-tree">
          <h4>Modules &amp; lessons</h4>

          {modules.map((m, mIdx) => (
            <div
              key={m.id}
              draggable={!isLocked}
              onDragStart={() => onModuleDragStart(mIdx)}
              onDragOver={onModuleDragOver}
              onDrop={() => onModuleDrop(mIdx)}
            >
              {/* Module row */}
              <div
                className="tree-module active"
                style={{ display: 'flex', alignItems: 'center', gap: 6 }}
              >
                {!isLocked && (
                  <span className="drag-handle" style={{ cursor: 'grab', flexShrink: 0 }}>
                    <Icon name="grip-vertical" size={13} />
                  </span>
                )}
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {m.title}
                </span>
                {!isLocked && (
                  <button
                    style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 2, flexShrink: 0 }}
                    onClick={() => deleteModule(m.id)}
                    title="Delete module"
                  >
                    <Icon name="trash-2" size={13} />
                  </button>
                )}
              </div>

              {/* Lesson rows */}
              {(m.lessons ?? []).map((l, lIdx) => (
                <div
                  key={l.id}
                  className={`tree-lesson${activeLesson?.lesson?.id === l.id ? ' active' : ''}`}
                  draggable={!isLocked}
                  onDragStart={() => onLessonDragStart(m.id, lIdx)}
                  onDragOver={onLessonDragOver}
                  onDrop={() => onLessonDrop(m.id, lIdx)}
                  onClick={() => selectLesson(l, m.id)}
                  style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}
                >
                  {!isLocked && (
                    <span className="drag-handle" style={{ flexShrink: 0 }}>
                      <Icon name="grip-vertical" size={13} />
                    </span>
                  )}
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
                    {l.title}
                  </span>
                  {!isLocked && (
                    <button
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 2, flexShrink: 0 }}
                      onClick={(e) => { e.stopPropagation(); deleteLesson(m.id, l.id); }}
                      title="Delete lesson"
                    >
                      <Icon name="trash-2" size={11} />
                    </button>
                  )}
                </div>
              ))}

              {/* Add lesson */}
              {!isLocked && addingLessonModuleId === m.id ? (
                <form
                  onSubmit={(e) => submitAddLesson(e, m.id)}
                  style={{ padding: '4px 8px 4px 24px', display: 'flex', gap: 6 }}
                >
                  <input
                    className="input"
                    style={{ fontSize: 13, padding: '4px 8px', flex: 1 }}
                    value={newLessonTitle}
                    onChange={e => setNewLessonTitle(e.target.value)}
                    placeholder="Lesson title"
                    autoFocus
                    onBlur={() => { if (!newLessonTitle.trim()) setAddingLessonModuleId(null); }}
                  />
                  <button type="submit" className="btn btn-primary btn-xs">Add</button>
                </form>
              ) : !isLocked && (
                <button
                  className="btn btn-secondary btn-xs"
                  style={{ marginLeft: 24, marginTop: 4, marginBottom: 4 }}
                  onClick={() => { setAddingLessonModuleId(m.id); setNewLessonTitle(''); }}
                >
                  <Icon name="plus" size={12} /> Add lesson
                </button>
              )}
            </div>
          ))}

          {/* Add module */}
          {!isLocked && (addingModule ? (
            <form onSubmit={submitAddModule} style={{ display: 'flex', gap: 6, marginTop: 12 }}>
              <input
                className="input"
                style={{ fontSize: 13, padding: '4px 8px', flex: 1 }}
                value={newModuleTitle}
                onChange={e => setNewModuleTitle(e.target.value)}
                placeholder="Module title"
                autoFocus
                onBlur={() => { if (!newModuleTitle.trim()) setAddingModule(false); }}
              />
              <button type="submit" className="btn btn-primary btn-xs">Add</button>
            </form>
          ) : (
            <button
              className="btn btn-secondary btn-sm"
              style={{ width: '100%', justifyContent: 'center', marginTop: 12 }}
              onClick={() => { setAddingModule(true); setNewModuleTitle(''); }}
            >
              <Icon name="plus" size={14} /> Add module
            </button>
          ))}
        </div>

        {/* Right — lesson editor */}
        <div className="builder-editor">
          {isLocked && (
            <div className="locked-banner">
              <Icon name="lock" size={16} />
              This course has active learners and can no longer be edited.
            </div>
          )}

          {!activeLesson ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--ink-3)', gap: 12 }}>
              <Icon name="mouse-pointer-click" size={36} color="var(--ink-4)" />
              <p style={{ margin: 0, fontSize: 14 }}>
                {isLocked ? 'Select a lesson to preview it.' : 'Select a lesson to edit, or add a new one.'}
              </p>
            </div>
          ) : (
            <>
              <div className="page-eyebrow" style={{ marginBottom: 4 }}>Lesson editor</div>
              <h2 style={{ fontFamily: 'var(--font-display)', fontWeight: 500, fontSize: 24, letterSpacing: '-0.01em', margin: '0 0 24px' }}>
                {activeLesson.lesson.title || 'Untitled lesson'}
              </h2>

              <div className="field">
                <label>Lesson title</label>
                <input
                  className="input"
                  value={lessonForm.title}
                  onChange={e => setLessonForm(p => ({ ...p, title: e.target.value }))}
                  disabled={isLocked}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <div className="field">
                  <label>Content type</label>
                  <select
                    className="input select"
                    value={lessonForm.contentType}
                    onChange={e => setLessonForm(p => ({ ...p, contentType: e.target.value }))}
                    disabled={isLocked}
                  >
                    <option value="VIDEO">Video</option>
                    <option value="DOCUMENT">Document</option>
                    <option value="ARTICLE">Article</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
                <div className="field">
                  <label>Content URL</label>
                  <input
                    className="input"
                    value={lessonForm.contentUrl}
                    onChange={e => setLessonForm(p => ({ ...p, contentUrl: e.target.value }))}
                    placeholder="https://…"
                    disabled={isLocked}
                  />
                </div>
              </div>

              <div className="field">
                <label>Lesson description</label>
                <textarea
                  className="input textarea"
                  value={lessonForm.description}
                  onChange={e => setLessonForm(p => ({ ...p, description: e.target.value }))}
                  rows={4}
                  disabled={isLocked}
                />
              </div>

              {!isLocked && (
                <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px solid var(--rule)', paddingTop: 18, marginTop: 8 }}>
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={() => deleteLesson(activeLesson.moduleId, activeLesson.lesson.id)}
                  >
                    <Icon name="trash-2" size={14} /> Delete lesson
                  </button>
                  <button className="btn btn-primary btn-sm" onClick={saveLesson} disabled={saving}>
                    {saving ? 'Saving…' : 'Save lesson'}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Publish modal */}
      {showPublish && (
        <Modal
          title="Publish course?"
          onClose={() => { setShowPublish(false); setPublishError(''); }}
          actions={
            <>
              <button
                className="btn btn-secondary"
                onClick={() => { setShowPublish(false); setPublishError(''); }}
              >
                Cancel
              </button>
              <button className="btn btn-primary" onClick={publish} disabled={publishing}>
                {publishing ? 'Publishing…' : 'Publish'}
              </button>
            </>
          }
        >
          <p>
            Once published, learners can discover and enrol in <strong>{courseTitle}</strong>.
            You can still edit it until the first learner starts.
          </p>
          {publishError && (
            <div style={{
              background: 'var(--danger-bg)',
              color: 'var(--danger)',
              borderRadius: 8,
              padding: '10px 14px',
              fontSize: 14,
              marginTop: 12,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}>
              <Icon name="circle-alert" size={14} />
              {publishError}
            </div>
          )}
        </Modal>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}

// ─── Main export ─────────────────────────────────────────────────────────────

export default function CourseBuilder() {
  const { id } = useParams();
  if (!id) return <Navigate to="/teach/courses" replace />;
  return <EditCourseBuilder courseId={id} />;
}
