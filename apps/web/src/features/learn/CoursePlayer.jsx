import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import ProgressBar from '../../components/ProgressBar';
import Notification from '../../components/Notification';
import AiChatDrawer from './AiChatDrawer';
import courseService from '../../services/courseService';
import enrolmentService from '../../services/enrolmentService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonLine } from '../../components/Skeleton';

export default function CoursePlayer() {
  const navigate = useNavigate();
  const { id: courseId } = useParams();

  const [resolvedModules, setResolvedModules] = useState([]);
  const [courseName, setCourseName]           = useState('');
  const [loading, setLoading]                 = useState(true);
  const [error, setError]                     = useState('');
  const [currentLessonId, setCurrentLessonId] = useState(null);
  const [completedIds, setCompletedIds]       = useState(new Set());
  const [showAI, setShowAI]                   = useState(false);
  const [toast, setToast]                     = useState('');

  useEffect(() => {
    if (!courseId) return;
    Promise.all([courseService.get(courseId), enrolmentService.listMine()])
      .then(([courseData, enrolData]) => {
        setCourseName(courseData.title);
        const cId = courseData.courseId ?? courseData.id;
        const match = (enrolData.items ?? []).find(e => e.courseId === cId);
        if (!match) throw new Error('Not enrolled in this course.');
        return enrolmentService.getProgress(match.enrolmentId).then(prog => ({ courseData, prog }));
      })
      .then(({ courseData, prog }) => {
        const pmMap = new Map((prog.modules ?? []).map(pm => [pm.moduleId, pm]));
        const merged = (courseData.modules ?? []).map(cm => {
          const pm = pmMap.get(cm.id) ?? { unlocked: false, lessons: [] };
          const plMap = new Map((pm.lessons ?? []).map(l => [l.lessonId, l]));
          return {
            moduleId:   cm.id,
            title:      cm.title,
            orderIndex: cm.orderIndex,
            unlocked:   pm.unlocked,
            lessons:    (cm.lessons ?? []).map(cl => {
              const pl = plMap.get(cl.id) ?? { completed: false };
              return {
                lessonId:    cl.id,
                title:       cl.title,
                orderIndex:  cl.orderIndex,
                contentType: cl.contentType,
                contentUrl:  cl.contentUrl ?? null,
                completed:   pl.completed,
              };
            }),
          };
        });
        setResolvedModules(merged);
        setCompletedIds(new Set(
          merged.flatMap(m => m.lessons.filter(l => l.completed).map(l => l.lessonId))
        ));
        if (prog.currentLessonId) {
          setCurrentLessonId(prog.currentLessonId);
        } else {
          const first = merged.find(m => m.unlocked)?.lessons?.[0];
          if (first) setCurrentLessonId(first.lessonId);
        }
      })
      .catch(err => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [courseId]);

  const allLessons = resolvedModules.flatMap(m => m.lessons);
  const lesson     = allLessons.find(l => l.lessonId === currentLessonId);
  const mod        = resolvedModules.find(m => m.lessons.some(l => l.lessonId === currentLessonId));
  const isComplete = completedIds.has(currentLessonId);
  const totalDone  = completedIds.size;
  const currentIdx = allLessons.findIndex(l => l.lessonId === currentLessonId);

  const markComplete = async () => {
    if (!currentLessonId || isComplete) return;
    const nextLesson = allLessons[currentIdx + 1];
    try {
      const result = await enrolmentService.completeLesson(currentLessonId);
      setCompletedIds(prev => new Set([...prev, currentLessonId]));
      setToast('Lesson complete.'); setTimeout(() => setToast(''), 2500);
      if (result.nextModuleId) {
        setResolvedModules(prev => prev.map(m =>
          m.moduleId === result.nextModuleId ? { ...m, unlocked: true } : m
        ));
      }
      if (nextLesson) {
        const nextMod = resolvedModules.find(m => m.lessons.some(l => l.lessonId === nextLesson.lessonId));
        const willUnlock = nextMod?.moduleId === result.nextModuleId || nextMod?.unlocked;
        if (willUnlock) setTimeout(() => setCurrentLessonId(nextLesson.lessonId), 800);
      }
    } catch (err) {
      setToast(getErrorMessage(err));
      setTimeout(() => setToast(''), 3000);
    }
  };

  if (loading) return (
    <div style={{ display: 'flex', height: 'calc(100vh - 60px)' }}>
      <div className="main" style={{ flex: 1 }}>
        <span className="skeleton" style={{ display: 'block', height: 360, borderRadius: 14, marginBottom: 20 }} />
        <SkeletonLine width="50%" height={22} />
      </div>
    </div>
  );
  if (error)   return <div className="main"><p style={{ color: 'var(--danger)' }}>{error}</p></div>;

  return (
    <div style={{ position: 'relative' }}>
      <div className="main" style={{ paddingBottom: 100, paddingRight: 360 }}>
        <button className="btn btn-ghost btn-sm" style={{ marginBottom: 20, marginLeft: -8 }} onClick={() => navigate(`/learn/courses/${courseId}`)}>
          <Icon name="arrow-left" size={15} /> {courseName}
        </button>

        <div>
          {lesson?.contentType === 'VIDEO' && lesson?.contentUrl && (
            <div className="video-block" style={{ padding: 0, background: 'transparent' }}>
              <iframe
                src={lesson.contentUrl}
                title={lesson.title}
                style={{ width: '100%', aspectRatio: '16/9', border: 'none', borderRadius: 14 }}
                allowFullScreen
              />
            </div>
          )}

          {lesson && lesson.contentType !== 'VIDEO' && lesson.contentUrl && (
            <div style={{ marginBottom: 16 }}>
              <a href={lesson.contentUrl} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm">
                <Icon name="file-text" size={14} /> Open resource
              </a>
            </div>
          )}

          {lesson && (
            <div className="lesson-body">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <span className="lesson-content-type">
                  <Icon name={
                    lesson.contentType === 'VIDEO' ? 'play-circle' :
                    lesson.contentType === 'DOCUMENT' ? 'file-text' : 'book-open'
                  } size={13} />
                  {lesson.contentType?.charAt(0) + (lesson.contentType?.slice(1).toLowerCase() ?? '')}
                </span>
              </div>
              <div className="lesson-breadcrumb">{mod?.title} / {lesson.title}</div>
              <h2 className="lesson-title">{lesson.title}</h2>
            </div>
          )}
        </div>
      </div>

      <div className="lesson-sidebar">
        <div className="lesson-tree">
          <div className="lesson-tree-head">
            <div className="lt-eyebrow">Course progress</div>
            <div className="lt-progress">
              <ProgressBar value={allLessons.length ? Math.round((totalDone / allLessons.length) * 100) : 0} />
              <span className="prog-label">{totalDone}/{allLessons.length}</span>
            </div>
          </div>
          {resolvedModules.map(m => (
            <div key={m.moduleId} className="module-block">
              <div className={`module-header${!m.unlocked ? ' locked' : ''}`}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {!m.unlocked
                    ? <Icon name="lock" size={13} color="var(--ink-4)" />
                    : <Icon name="chevron-down" size={13} color="var(--ink-3)" />}
                  <span className="mod-title">{m.title}</span>
                </div>
                <span className="mod-count">
                  {m.lessons.filter(l => completedIds.has(l.lessonId)).length}/{m.lessons.length}
                </span>
              </div>
              {m.unlocked && m.lessons.map((l, i) => (
                <div
                  key={l.lessonId}
                  className={`lesson-item${l.lessonId === currentLessonId ? ' active' : ''}`}
                  onClick={() => setCurrentLessonId(l.lessonId)}
                  style={{ cursor: 'pointer' }}
                >
                  <div className={`lesson-dot${completedIds.has(l.lessonId) ? ' done' : l.lessonId === currentLessonId ? ' current' : ''}`}>
                    {completedIds.has(l.lessonId) ? '✓' : i + 1}
                  </div>
                  <span style={{ lineHeight: 1.3 }}>{l.title}</span>
                  <span className="lesson-time" style={{ fontSize: 11, textTransform: 'capitalize' }}>
                    {l.contentType?.toLowerCase()}
                  </span>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>

      <div className="lesson-footer">
        <button
          className="btn btn-secondary btn-sm"
          disabled={currentIdx <= 0}
          onClick={() => { if (currentIdx > 0) setCurrentLessonId(allLessons[currentIdx - 1].lessonId); }}
        >
          <Icon name="chevron-left" size={15} /> Previous
        </button>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <button
            className={`btn btn-sm ${isComplete ? 'btn-secondary' : 'btn-pulse'}`}
            onClick={markComplete}
            disabled={isComplete || !mod?.unlocked}
          >
            {isComplete ? <><Icon name="check" size={14} /> Completed ✓</> : 'Mark as complete'}
          </button>
          <button
            className="btn btn-primary btn-sm"
            disabled={currentIdx >= allLessons.length - 1 || (() => {
              const next = allLessons[currentIdx + 1];
              if (!next) return true;
              const nextMod = resolvedModules.find(m => m.lessons.some(l => l.lessonId === next.lessonId));
              return !nextMod?.unlocked;
            })()}
            onClick={() => {
              const next = allLessons[currentIdx + 1];
              if (next) {
                const nextMod = resolvedModules.find(m => m.lessons.some(l => l.lessonId === next.lessonId));
                if (nextMod?.unlocked) setCurrentLessonId(next.lessonId);
              }
            }}
          >
            Next <Icon name="chevron-right" size={15} />
          </button>
        </div>
      </div>

      <button className="ai-fab" style={{ right: showAI ? 440 : 24 }} onClick={() => setShowAI(!showAI)}>
        <Icon name="sparkles" size={15} /> Ask AI
      </button>

      {showAI && <AiChatDrawer courseName={courseName} onClose={() => setShowAI(false)} />}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
