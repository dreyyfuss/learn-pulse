import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Navigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import LessonContentUpload from '../../components/LessonContentUpload';
import QuizEditor from '../../components/QuizEditor';
import courseService from '../../services/courseService';
import { getErrorMessage } from '../../utils/errorMessages';


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
  const [activeQuiz, setActiveQuiz]     = useState(null); // { quiz, moduleId }
  const [lessonForm, setLessonForm] = useState({ title: '', contentType: 'VIDEO', contentUrl: '', description: '' });

  // Inline-add state
  const [addingModule, setAddingModule]               = useState(false);
  const [newModuleTitle, setNewModuleTitle]            = useState('');
  const [addingLessonModuleId, setAddingLessonModuleId] = useState(null);
  const [newLessonTitle, setNewLessonTitle]            = useState('');
  const [addingQuizModuleId, setAddingQuizModuleId]   = useState(null);
  const [newQuizTitle, setNewQuizTitle]               = useState('');

  // UI state
  const [saving, setSaving]         = useState(false);
  const [showPublish, setShowPublish] = useState(false);
  const [publishError, setPublishError] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [toast, setToast]           = useState('');
  const [enrolmentCode, setEnrolmentCode] = useState(null);

  // DnD refs
  const dragModuleIdx = useRef(null);
  const dragItem      = useRef({ moduleId: null, idx: null });

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

  // ── Load course ──
  useEffect(() => {
    courseService.get(courseId)
      .then(data => {
        setCourse(data);
        setCourseTitle(data.title);
        setModules(data.modules ?? []);
        if (data.visibility === 'PRIVATE') {
          courseService.getEnrolmentCode(courseId)
            .then(res => setEnrolmentCode(res.enrolmentCode))
            .catch(() => {});
        }
      })
      .catch(err => setLoadError(getErrorMessage(err)))
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
      showToast(getErrorMessage(err));
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
      setPublishError(getErrorMessage(err));
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
      setModules(prev => [...prev, { ...mod, quizzes: [] }]);
      setNewModuleTitle('');
      setAddingModule(false);
    } catch (err) {
      showToast(getErrorMessage(err));
    }
  };

  // ── Delete module ──
  const deleteModule = async (moduleId) => {
    try {
      await courseService.deleteModule(courseId, moduleId);
      setModules(prev => prev.filter(m => m.id !== moduleId));
      if (activeLesson?.moduleId === moduleId) setActiveLesson(null);
      if (activeQuiz?.moduleId === moduleId) setActiveQuiz(null);
    } catch (err) {
      showToast(getErrorMessage(err));
    }
  };

  // ── Add lesson ──
  const submitAddLesson = async (e, moduleId) => {
    e?.preventDefault();
    if (!newLessonTitle.trim()) return;
    const mod = modules.find(m => m.id === moduleId);
    const itemCount = (mod?.lessons?.length ?? 0) + (mod?.quizzes?.length ?? 0);
    try {
      const lesson = await courseService.createLesson(courseId, moduleId, {
        title: newLessonTitle.trim(),
        contentType: 'VIDEO',
        orderIndex: itemCount,
      });
      setModules(prev => prev.map(m =>
        m.id === moduleId ? { ...m, lessons: [...(m.lessons ?? []), lesson] } : m
      ));
      setNewLessonTitle('');
      setAddingLessonModuleId(null);
    } catch (err) {
      showToast(getErrorMessage(err));
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
      showToast(getErrorMessage(err));
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
      showToast(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  // ── Add quiz ──
  const submitAddQuiz = async (e, moduleId) => {
    e?.preventDefault();
    if (!newQuizTitle.trim()) return;
    const mod = modules.find(m => m.id === moduleId);
    const itemCount = (mod?.lessons?.length ?? 0) + (mod?.quizzes?.length ?? 0);
    try {
      const quiz = await courseService.createQuiz(courseId, moduleId, {
        title: newQuizTitle.trim(),
        orderIndex: itemCount,
      });
      const quizData = quiz.data ?? quiz;
      setModules(prev => prev.map(m =>
        m.id === moduleId ? { ...m, quizzes: [...(m.quizzes ?? []), quizData] } : m
      ));
      setNewQuizTitle('');
      setAddingQuizModuleId(null);
      setActiveQuiz({ quiz: quizData, moduleId });
      setActiveLesson(null);
    } catch (err) {
      showToast(getErrorMessage(err));
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
      await courseService.reorderModules(
        courseId,
        ordered.map(m => ({ id: m.id, orderIndex: m.orderIndex }))
      );
    } catch {
      showToast('Failed to save module order.');
    }
  };

  // ── DnD — items (lessons + quizzes) ──
  const onItemDragStart = (moduleId, idx) => { dragItem.current = { moduleId, idx }; };
  const onItemDragOver  = (e) => e.preventDefault();
  const onItemDrop = async (targetModuleId, targetIIdx) => {
    const { moduleId: fromModuleId, idx: fromIIdx } = dragItem.current;
    if (fromModuleId !== targetModuleId || fromIIdx === targetIIdx) return;
    const mIdx = modules.findIndex(m => m.id === targetModuleId);
    const mod = modules[mIdx];
    const merged = [
      ...(mod.lessons ?? []).map(l => ({ ...l, _type: 'lesson' })),
      ...(mod.quizzes ?? []).map(q => ({ ...q, _type: 'quiz' })),
    ].sort((a, b) => a.orderIndex - b.orderIndex);
    const [moved] = merged.splice(fromIIdx, 1);
    merged.splice(targetIIdx, 0, moved);
    const reordered = merged.map((item, i) => ({ ...item, orderIndex: i }));
    const newLessons = reordered.filter(item => item._type === 'lesson');
    const newQuizzes = reordered.filter(item => item._type === 'quiz');
    setModules(prev => prev.map((m, i) => i === mIdx ? { ...m, lessons: newLessons, quizzes: newQuizzes } : m));
    dragItem.current = { moduleId: null, idx: null };
    try {
      const promises = [];
      if (newLessons.length > 0)
        promises.push(courseService.reorderLessons(courseId, targetModuleId, newLessons.map(l => ({ id: l.id, orderIndex: l.orderIndex }))));
      if (newQuizzes.length > 0)
        promises.push(courseService.reorderQuizzes(courseId, targetModuleId, newQuizzes.map(q => ({ id: q.id, orderIndex: q.orderIndex }))));
      await Promise.all(promises);
    } catch {
      showToast('Failed to save order.');
    }
  };

  // ── Select lesson ──
  const selectLesson = (lesson, moduleId) => {
    setActiveLesson({ lesson, moduleId });
    setActiveQuiz(null);
    setLessonForm({
      title:       lesson.title       ?? '',
      contentType: lesson.contentType ?? 'VIDEO',
      contentUrl:  lesson.contentUrl  ?? '',
      description: lesson.description ?? '',
    });
  };

  // ── Select quiz ──
  const selectQuiz = (quiz, moduleId) => {
    setActiveQuiz({ quiz, moduleId });
    setActiveLesson(null);
  };

  // ── Mark lesson as having content after upload (optimistic update) ──
  const markLessonHasContent = () => {
    if (!activeLesson) return;
    const { lesson, moduleId } = activeLesson;
    const updated = { ...lesson, contentKey: '__uploaded__' };
    setModules(prev => prev.map(m =>
      m.id === moduleId
        ? { ...m, lessons: m.lessons.map(l => l.id === lesson.id ? updated : l) }
        : m
    ));
    setActiveLesson({ lesson: updated, moduleId });
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
        {enrolmentCode && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'var(--surface-2)', border: '1px solid var(--rule)', borderRadius: 8, padding: '4px 10px', fontSize: 13 }}>
            <Icon name="key" size={13} color="var(--ink-3)" />
            <span style={{ color: 'var(--ink-2)', userSelect: 'all', fontFamily: 'monospace', letterSpacing: '0.05em' }}>{enrolmentCode}</span>
            <button
              style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 2, color: 'var(--ink-3)', display: 'flex', alignItems: 'center' }}
              title="Copy enrolment code"
              onClick={() => { navigator.clipboard.writeText(enrolmentCode); showToast('Enrolment code copied.'); }}
            >
              <Icon name="copy" size={13} />
            </button>
          </div>
        )}
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

          {modules.map((m, mIdx) => {
            // Merge lessons + quizzes sorted by orderIndex for display
            const items = [
              ...(m.lessons ?? []).map(l => ({ ...l, _type: 'lesson' })),
              ...(m.quizzes ?? []).map(q => ({ ...q, _type: 'quiz' })),
            ].sort((a, b) => a.orderIndex - b.orderIndex);

            return (
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

                {/* Lesson + Quiz rows (merged, sorted by orderIndex) */}
                {items.map((item, iIdx) => {
                  const isLesson = item._type === 'lesson';
                  const isActive = isLesson
                    ? activeLesson?.lesson?.id === item.id
                    : activeQuiz?.quiz?.id === item.id;

                  return (
                    <div
                      key={item.id}
                      className={`tree-lesson${isActive ? ' active' : ''}`}
                      draggable={!isLocked}
                      onDragStart={(e) => { e.stopPropagation(); onItemDragStart(m.id, iIdx); }}
                      onDragOver={onItemDragOver}
                      onDrop={(e) => { e.stopPropagation(); onItemDrop(m.id, iIdx); }}
                      onClick={() => isLesson ? selectLesson(item, m.id) : selectQuiz(item, m.id)}
                      style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}
                    >
                      {!isLocked && (
                        <span className="drag-handle" style={{ flexShrink: 0 }}>
                          <Icon name="grip-vertical" size={13} />
                        </span>
                      )}
                      {!isLesson && (
                        <span style={{ flexShrink: 0, color: 'var(--ink-4)', display: 'flex', alignItems: 'center' }}>
                          <Icon name="help-circle" size={13} />
                        </span>
                      )}
                      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 13 }}>
                        {item.title}
                      </span>
                      {!isLocked && (
                        <button
                          style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink-4)', padding: 2, flexShrink: 0 }}
                          onClick={(e) => {
                            e.stopPropagation();
                            isLesson ? deleteLesson(m.id, item.id) : handleDeleteQuiz(m.id, item.id);
                          }}
                          title={isLesson ? 'Delete lesson' : 'Delete quiz'}
                        >
                          <Icon name="trash-2" size={11} />
                        </button>
                      )}
                    </div>
                  );
                })}

                {/* Add lesson inline form */}
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
                ) : !isLocked && addingQuizModuleId === m.id ? (
                  <form
                    onSubmit={(e) => submitAddQuiz(e, m.id)}
                    style={{ padding: '4px 8px 4px 24px', display: 'flex', gap: 6 }}
                  >
                    <input
                      className="input"
                      style={{ fontSize: 13, padding: '4px 8px', flex: 1 }}
                      value={newQuizTitle}
                      onChange={e => setNewQuizTitle(e.target.value)}
                      placeholder="Quiz title"
                      autoFocus
                      onBlur={() => { if (!newQuizTitle.trim()) setAddingQuizModuleId(null); }}
                    />
                    <button type="submit" className="btn btn-primary btn-xs">Add</button>
                  </form>
                ) : !isLocked && (
                  <div style={{ display: 'flex', gap: 4, marginLeft: 24, marginTop: 4, marginBottom: 4 }}>
                    <button
                      className="btn btn-secondary btn-xs"
                      onClick={() => { setAddingLessonModuleId(m.id); setAddingQuizModuleId(null); setNewLessonTitle(''); }}
                    >
                      <Icon name="plus" size={12} /> Lesson
                    </button>
                    <button
                      className="btn btn-secondary btn-xs"
                      onClick={() => { setAddingQuizModuleId(m.id); setAddingLessonModuleId(null); setNewQuizTitle(''); }}
                    >
                      <Icon name="help-circle" size={12} /> Quiz
                    </button>
                  </div>
                )}
              </div>
            );
          })}

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

        {/* Right — editor panel */}
        <div className="builder-editor">
          {isLocked && (
            <div className="locked-banner">
              <Icon name="lock" size={16} />
              This course has active learners and can no longer be edited.
            </div>
          )}

          {activeQuiz ? (
            <QuizEditor
              courseId={courseId}
              moduleId={activeQuiz.moduleId}
              quiz={activeQuiz.quiz}
              isLocked={isLocked}
              onUpdated={(updatedQuiz) => {
                setModules(prev => prev.map(m =>
                  m.id === activeQuiz.moduleId
                    ? { ...m, quizzes: (m.quizzes ?? []).map(q => q.id === updatedQuiz.id ? updatedQuiz : q) }
                    : m
                ));
                setActiveQuiz({ quiz: updatedQuiz, moduleId: activeQuiz.moduleId });
              }}
              onDeleted={() => {
                setModules(prev => prev.map(m =>
                  m.id === activeQuiz.moduleId
                    ? { ...m, quizzes: (m.quizzes ?? []).filter(q => q.id !== activeQuiz.quiz.id) }
                    : m
                ));
                setActiveQuiz(null);
              }}
            />
          ) : !activeLesson ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--ink-3)', gap: 12 }}>
              <Icon name="mouse-pointer-click" size={36} color="var(--ink-4)" />
              <p style={{ margin: 0, fontSize: 14 }}>
                {isLocked ? 'Select a lesson or quiz to preview it.' : 'Select a lesson or quiz to edit, or add a new one.'}
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

              <div className="field">
                <label>Content type</label>
                <select
                  className="input select"
                  value={lessonForm.contentType}
                  onChange={e => setLessonForm(p => ({ ...p, contentType: e.target.value }))}
                  disabled={isLocked}
                  style={{ maxWidth: 200 }}
                >
                  <option value="VIDEO">Video</option>
                  <option value="DOCUMENT">Document</option>
                  <option value="ARTICLE">Article</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>

              {!isLocked && (
                <LessonContentUpload
                  courseId={courseId}
                  moduleId={activeLesson.moduleId}
                  lessonId={activeLesson.lesson.id}
                  contentType={lessonForm.contentType}
                  hasContent={!!activeLesson.lesson.contentKey}
                  onUploaded={markLessonHasContent}
                />
              )}

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

  function handleDeleteQuiz(moduleId, quizId) {
    courseService.deleteQuiz(courseId, moduleId, quizId)
      .then(() => {
        setModules(prev => prev.map(m =>
          m.id === moduleId ? { ...m, quizzes: (m.quizzes ?? []).filter(q => q.id !== quizId) } : m
        ));
        if (activeQuiz?.quiz?.id === quizId) setActiveQuiz(null);
      })
      .catch(err => showToast(getErrorMessage(err)));
  }
}

// ─── Main export ─────────────────────────────────────────────────────────────

export default function CourseBuilder() {
  const { id } = useParams();
  if (!id) return <Navigate to="/teach/courses" replace />;
  return <EditCourseBuilder courseId={id} />;
}
