import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import ProgressBar from '../../components/ProgressBar';
import Notification from '../../components/Notification';
import AiChatDrawer from './AiChatDrawer';
import LessonContentViewer from '../../components/LessonContentViewer';
import QuizPlayer from '../../components/QuizPlayer';
import courseService from '../../services/courseService';
import enrolmentService from '../../services/enrolmentService';
import { getErrorMessage } from '../../utils/errorMessages';
import { SkeletonLine } from '../../components/Skeleton';
import certificateService from '../../services/certificateService';

export default function CoursePlayer() {
  const navigate = useNavigate();
  const { id: courseId } = useParams();

  const [resolvedModules, setResolvedModules] = useState([]);
  const [courseName, setCourseName]           = useState('');
  const [loading, setLoading]                 = useState(true);
  const [error, setError]                     = useState('');
  const [currentItemId, setCurrentItemId]     = useState(null);
  const [completedIds, setCompletedIds]       = useState(new Set()); // lesson IDs
  const [passedQuizIds, setPassedQuizIds]     = useState(new Set()); // quiz IDs
  const [showAI, setShowAI]                   = useState(false);
  const [showLessonPanel, setShowLessonPanel] = useState(false);
  const [toast, setToast]                     = useState('');
  const [celebration, setCelebration]         = useState(false);
  const [certUuid, setCertUuid]               = useState(null);
  const [certPolling, setCertPolling]         = useState(false);
  const pollRef                               = useRef(null);

  useEffect(() => {
    if (!courseId) {
      enrolmentService.listMine()
        .then(enrolData => {
          const items = enrolData.items ?? [];
          const resume = items.find(e => e.startedAt != null && e.status === 'ACTIVE');
          if (resume) {
            navigate(`/learn/courses/${resume.courseId}/play`, { replace: true });
          } else {
            navigate('/learn/dashboard', { replace: true });
          }
        })
        .catch(() => navigate('/learn/dashboard', { replace: true }));
      return;
    }
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
          const pm = pmMap.get(cm.id) ?? { unlocked: false, lessons: [], quizzes: [] };
          const plMap = new Map((pm.lessons ?? []).map(l => [l.lessonId, l]));
          const pqMap = new Map((pm.quizzes ?? []).map(q => [q.quizId, q]));
          return {
            moduleId:   cm.id,
            title:      cm.title,
            orderIndex: cm.orderIndex,
            unlocked:   pm.unlocked,
            lessons: (cm.lessons ?? []).map(cl => {
              const pl = plMap.get(cl.id) ?? { completed: false };
              return {
                itemId:      cl.id,
                _type:       'lesson',
                title:       cl.title,
                orderIndex:  cl.orderIndex,
                contentType: cl.contentType,
                contentUrl:  cl.contentUrl ?? null,
                completed:   pl.completed,
              };
            }),
            quizzes: (cm.quizzes ?? []).map(cq => {
              const pq = pqMap.get(cq.id) ?? { passed: false };
              return {
                itemId:     cq.id,
                _type:      'quiz',
                title:      cq.title,
                orderIndex: cq.orderIndex,
                passed:     pq.passed,
              };
            }),
          };
        });
        setResolvedModules(merged);

        const doneLesson = new Set(
          merged.flatMap(m => m.lessons.filter(l => l.completed).map(l => l.itemId))
        );
        const doneQuiz = new Set(
          merged.flatMap(m => m.quizzes.filter(q => q.passed).map(q => q.itemId))
        );
        setCompletedIds(doneLesson);
        setPassedQuizIds(doneQuiz);

        // Determine starting item
        const firstIncomplete = merged
          .filter(m => m.unlocked)
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .flatMap(m => [
            ...m.lessons.map(l => l),
            ...m.quizzes.map(q => q),
          ].sort((a, b) => a.orderIndex - b.orderIndex))
          .find(item => item._type === 'lesson' ? !item.completed : !item.passed);

        if (firstIncomplete) {
          setCurrentItemId(firstIncomplete.itemId);
        } else {
          // All done — start at first item of first unlocked module
          const firstItem = merged.find(m => m.unlocked)
            ?.lessons?.concat(merged.find(m => m.unlocked)?.quizzes ?? [])
            ?.sort((a, b) => a.orderIndex - b.orderIndex)?.[0];
          if (firstItem) setCurrentItemId(firstItem.itemId);
        }
      })
      .catch(err => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [courseId]);

  useEffect(() => () => { if (pollRef.current) clearInterval(pollRef.current); }, []);

  // Flat list of all items across all modules, sorted by module order then item order
  const allItems = resolvedModules
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .flatMap(m => [
      ...m.lessons,
      ...m.quizzes,
    ].sort((a, b) => a.orderIndex - b.orderIndex));

  const currentItem = allItems.find(i => i.itemId === currentItemId);
  const mod         = resolvedModules.find(m =>
    [...m.lessons, ...m.quizzes].some(i => i.itemId === currentItemId)
  );
  const isLesson    = currentItem?._type === 'lesson';
  const isComplete  = isLesson
    ? completedIds.has(currentItemId)
    : passedQuizIds.has(currentItemId);
  const totalDone   = completedIds.size + passedQuizIds.size;
  const currentIdx  = allItems.findIndex(i => i.itemId === currentItemId);

  const startCertPoll = (courseIdToWatch) => {
    setCertPolling(true);
    const deadline = Date.now() + 30_000;
    pollRef.current = setInterval(async () => {
      try {
        const res = await certificateService.listMine();
        const certs = res.data ?? res;
        const match = certs.find(c => c.courseId === courseIdToWatch);
        if (match) {
          clearInterval(pollRef.current);
          setCertUuid(match.certificateUuid);
          setCertPolling(false);
        } else if (Date.now() > deadline) {
          clearInterval(pollRef.current);
          setCertPolling(false);
        }
      } catch {
        clearInterval(pollRef.current);
        setCertPolling(false);
      }
    }, 3000);
  };

  const markComplete = async () => {
    if (!currentItemId || isComplete || !isLesson) return;
    const nextItem = allItems[currentIdx + 1];
    try {
      const result = await enrolmentService.completeLesson(currentItemId);
      setCompletedIds(prev => new Set([...prev, currentItemId]));
      if (result.courseCompleted) {
        setCelebration(true);
        startCertPoll(courseId);
        return;
      }
      setToast('Lesson complete.'); setTimeout(() => setToast(''), 2500);
      if (result.nextModuleId) {
        setResolvedModules(prev => prev.map(m =>
          m.moduleId === result.nextModuleId ? { ...m, unlocked: true } : m
        ));
      }
      if (nextItem) {
        const nextMod = resolvedModules.find(m =>
          [...m.lessons, ...m.quizzes].some(i => i.itemId === nextItem.itemId)
        );
        const willUnlock = nextMod?.moduleId === result.nextModuleId || nextMod?.unlocked;
        if (willUnlock) setTimeout(() => setCurrentItemId(nextItem.itemId), 800);
      }
    } catch (err) {
      setToast(getErrorMessage(err));
      setTimeout(() => setToast(''), 3000);
    }
  };

  const handleQuizPassed = ({ nextModuleId, courseCompleted }) => {
    setPassedQuizIds(prev => new Set([...prev, currentItemId]));
    if (courseCompleted) {
      setCelebration(true);
      startCertPoll(courseId);
      return;
    }
    setToast('Quiz passed!'); setTimeout(() => setToast(''), 2500);
    if (nextModuleId) {
      setResolvedModules(prev => prev.map(m =>
        m.moduleId === nextModuleId ? { ...m, unlocked: true } : m
      ));
    }
    const nextItem = allItems[currentIdx + 1];
    if (nextItem) {
      const nextMod = resolvedModules.find(m =>
        [...m.lessons, ...m.quizzes].some(i => i.itemId === nextItem.itemId)
      );
      const willUnlock = nextMod?.moduleId === nextModuleId || nextMod?.unlocked;
      if (willUnlock) setTimeout(() => setCurrentItemId(nextItem.itemId), 800);
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

  if (celebration) return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.82)',
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', zIndex: 9999, gap: 24,
    }}>
      <div style={{ fontSize: 64 }}>🎓</div>
      <h1 style={{ color: '#fff', margin: 0, fontSize: 32, textAlign: 'center' }}>
        Course complete!
      </h1>
      <p style={{ color: 'rgba(255,255,255,0.7)', margin: 0, textAlign: 'center', maxWidth: 400 }}>
        {courseName}
      </p>
      {certPolling && !certUuid && (
        <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: 14 }}>
          Generating your certificate…
        </p>
      )}
      {certUuid && (
        <button
          className="btn btn-primary"
          onClick={() => {
            certificateService.downloadUrl(certUuid)
              .then(url => window.open(url, '_blank', 'noopener'))
              .catch(() => { setToast('Download unavailable. Check My Certificates.'); setTimeout(() => setToast(''), 3500); });
          }}
        >
          <Icon name="download" size={15} /> Download Certificate
        </button>
      )}
      {!certPolling && !certUuid && (
        <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: 14 }}>
          Certificate is on its way — check My Certificates in a moment.
        </p>
      )}
      <button
        className="btn btn-secondary btn-sm"
        onClick={() => { setCelebration(false); navigate('/learn/dashboard'); }}
        style={{ marginTop: 8 }}
      >
        Back to dashboard
      </button>
    </div>
  );

  return (
    <div style={{ position: 'relative' }}>
      <div className="main player-content">
        <button className="btn btn-ghost btn-sm" style={{ marginBottom: 20, marginLeft: -8 }} onClick={() => navigate(`/learn/courses/${courseId}`)}>
          <Icon name="arrow-left" size={15} /> {courseName}
        </button>

        <div>
          {currentItem && isLesson && (
            <LessonContentViewer
              courseId={courseId}
              moduleId={mod?.moduleId}
              lessonId={currentItem.itemId}
            />
          )}

          {currentItem && !isLesson && (
            <div style={{ paddingTop: 8 }}>
              <QuizPlayer
                key={currentItem.itemId}
                quizId={currentItem.itemId}
                onPassed={handleQuizPassed}
              />
            </div>
          )}

          {currentItem && isLesson && (
            <div className="lesson-body">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <span className="lesson-content-type">
                  <Icon name={
                    currentItem.contentType === 'VIDEO' ? 'play-circle' :
                    currentItem.contentType === 'DOCUMENT' ? 'file-text' : 'book-open'
                  } size={13} />
                  {currentItem.contentType?.charAt(0) + (currentItem.contentType?.slice(1).toLowerCase() ?? '')}
                </span>
              </div>
              <div className="lesson-breadcrumb">{mod?.title} / {currentItem.title}</div>
              <h2 className="lesson-title">{currentItem.title}</h2>
            </div>
          )}
        </div>
      </div>

      {showLessonPanel && (
        <div className="lesson-panel-backdrop active" onClick={() => setShowLessonPanel(false)} />
      )}

      <div className={`lesson-sidebar${showLessonPanel ? ' panel-open' : ''}`}>
        <div className="lesson-tree">
          <div className="lesson-tree-head">
            <div className="lt-eyebrow">Course progress</div>
            <div className="lt-progress">
              <ProgressBar value={allItems.length ? Math.round((totalDone / allItems.length) * 100) : 0} />
              <span className="prog-label">{totalDone}/{allItems.length}</span>
            </div>
          </div>
          {resolvedModules.map(m => {
            const moduleItems = [...m.lessons, ...m.quizzes].sort((a, b) => a.orderIndex - b.orderIndex);
            const moduleDone  = m.lessons.filter(l => completedIds.has(l.itemId)).length
                              + m.quizzes.filter(q => passedQuizIds.has(q.itemId)).length;
            return (
              <div key={m.moduleId} className="module-block">
                <div className={`module-header${!m.unlocked ? ' locked' : ''}`}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    {!m.unlocked
                      ? <Icon name="lock" size={13} color="var(--ink-4)" />
                      : <Icon name="chevron-down" size={13} color="var(--ink-3)" />}
                    <span className="mod-title">{m.title}</span>
                  </div>
                  <span className="mod-count">{moduleDone}/{moduleItems.length}</span>
                </div>
                {m.unlocked && moduleItems.map((item, i) => {
                  const done = item._type === 'lesson'
                    ? completedIds.has(item.itemId)
                    : passedQuizIds.has(item.itemId);
                  return (
                    <div
                      key={item.itemId}
                      className={`lesson-item${item.itemId === currentItemId ? ' active' : ''}`}
                      onClick={() => { setCurrentItemId(item.itemId); setShowLessonPanel(false); }}
                      style={{ cursor: 'pointer' }}
                    >
                      <div className={`lesson-dot${done ? ' done' : item.itemId === currentItemId ? ' current' : ''}`}>
                        {done ? '✓' : i + 1}
                      </div>
                      <span style={{ lineHeight: 1.3 }}>{item.title}</span>
                      <span className="lesson-time" style={{ fontSize: 11 }}>
                        {item._type === 'quiz' ? (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                            <Icon name="help-circle" size={10} /> Quiz
                          </span>
                        ) : (
                          item.contentType?.toLowerCase()
                        )}
                      </span>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      <div className="lesson-footer">
        <button
          className="btn btn-secondary btn-sm"
          disabled={currentIdx <= 0}
          onClick={() => { if (currentIdx > 0) setCurrentItemId(allItems[currentIdx - 1].itemId); }}
        >
          <Icon name="chevron-left" size={15} /> Previous
        </button>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          {isLesson && (
            <button
              className={`btn btn-sm ${isComplete ? 'btn-secondary' : 'btn-pulse'}`}
              onClick={markComplete}
              disabled={isComplete || !mod?.unlocked}
            >
              {isComplete ? <><Icon name="check" size={14} /> Completed ✓</> : 'Mark as complete'}
            </button>
          )}
          <button
            className="btn btn-primary btn-sm"
            disabled={currentIdx >= allItems.length - 1 || (() => {
              const next = allItems[currentIdx + 1];
              if (!next) return true;
              const nextMod = resolvedModules.find(m =>
                [...m.lessons, ...m.quizzes].some(i => i.itemId === next.itemId)
              );
              return !nextMod?.unlocked;
            })()}
            onClick={() => {
              const next = allItems[currentIdx + 1];
              if (next) {
                const nextMod = resolvedModules.find(m =>
                  [...m.lessons, ...m.quizzes].some(i => i.itemId === next.itemId)
                );
                if (nextMod?.unlocked) setCurrentItemId(next.itemId);
              }
            }}
          >
            Next <Icon name="chevron-right" size={15} />
          </button>
        </div>
      </div>

      <button
        className="btn btn-primary lesson-panel-btn"
        onClick={() => setShowLessonPanel(o => !o)}
      >
        <Icon name="layout-dashboard" size={14} />
        Lessons ({allItems.length ? `${totalDone}/${allItems.length}` : '…'})
      </button>

      <button className="ai-fab" style={{ right: showAI ? 440 : 24 }} onClick={() => setShowAI(!showAI)}>
        <Icon name="sparkles" size={15} /> Ask AI
      </button>

      {showAI && <AiChatDrawer courseId={courseId} courseName={courseName} onClose={() => setShowAI(false)} />}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
