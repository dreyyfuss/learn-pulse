import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import Avatar from '../../components/Avatar';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import ProgressBar from '../../components/ProgressBar';
import courseService from '../../services/courseService';
import enrolmentService from '../../services/enrolmentService';

export default function CourseDetail() {
  const navigate = useNavigate();
  const { id } = useParams();

  const [course, setCourse]         = useState(null);
  const [enrolment, setEnrolment]   = useState(null);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [showModal, setShowModal]   = useState(false);
  const [enrollCode, setEnrollCode] = useState('');
  const [codeError, setCodeError]   = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast]           = useState('');
  const [openModules, setOpenModules] = useState(() => new Set());

  const toggleModule = id => setOpenModules(prev => {
    const next = new Set(prev);
    next.has(id) ? next.delete(id) : next.add(id);
    return next;
  });

  const showToast = (msg, ms = 3000) => { setToast(msg); setTimeout(() => setToast(''), ms); };

  useEffect(() => {
    Promise.all([courseService.get(id), enrolmentService.listMine()])
      .then(([courseData, enrolData]) => {
        setCourse(courseData);
        setOpenModules(new Set((courseData.modules ?? []).map(m => m.id)));
        const courseId = courseData.courseId ?? courseData.id;
        const match = (enrolData.items ?? []).find(e => e.courseId === courseId);
        setEnrolment(match ?? null);
      })
      .catch(err => setError(err.message || 'Could not load course.'))
      .finally(() => setLoading(false));
  }, [id]);

  const isEnrolled = !!enrolment;
  const isStarted  = isEnrolled && enrolment.startedAt != null;
  const isPrivate  = course?.visibility === 'PRIVATE';
  const courseId   = course?.courseId ?? course?.id;

  const handlePublicEnrol = async () => {
    setSubmitting(true);
    try {
      const result = await enrolmentService.enrol(courseId);
      setEnrolment({
        ...result,
        courseId,
        courseTitle: course.title,
        enrolledAt: new Date().toISOString(),
        startedAt: null,
        progressPercent: 0,
      });
      showToast('Enrolled! Click "Start Course" when ready.', 4000);
    } catch (err) {
      if (err.status === 409) {
        enrolmentService.listMine().then(d => {
          const match = (d.items ?? []).find(e => e.courseId === courseId);
          setEnrolment(match ?? null);
        });
      } else {
        showToast(err.message || 'Could not enrol.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleStart = async () => {
    setSubmitting(true);
    try {
      await enrolmentService.startEnrolment(enrolment.enrolmentId);
      navigate(`/learn/courses/${id}/play`);
    } catch (err) {
      showToast('Could not start: ' + err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleModalSubmit = async () => {
    setCodeError('');
    setSubmitting(true);
    try {
      const result = await enrolmentService.enrol(courseId, enrollCode.trim());
      setShowModal(false);
      setEnrolment({
        ...result,
        courseId,
        courseTitle: course.title,
        enrolledAt: new Date().toISOString(),
        startedAt: null,
        progressPercent: 0,
      });
      showToast('Enrolled! Click "Start Course" when ready.', 4000);
    } catch (err) {
      const code = err.data?.error?.code;
      if (code === 'ENROLMENT_CODE_INVALID' || err.status === 403) {
        setCodeError('That code is invalid. Please check and try again.');
      } else if (code === 'ALREADY_ENROLLED' || err.status === 409) {
        setShowModal(false);
        enrolmentService.listMine().then(d => {
          const match = (d.items ?? []).find(e => e.courseId === courseId);
          setEnrolment(match ?? null);
        });
      } else {
        setCodeError(err.message || 'Something went wrong.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="main"><p style={{ color: 'var(--ink-3)' }}>Loading…</p></div>;
  if (error)   return <div className="main"><p style={{ color: 'var(--danger)' }}>{error}</p></div>;

  const totalLessons = course.modules?.reduce((a, m) => a + (m.lessons?.length ?? 0), 0) ?? 0;
  const moduleCount  = course.modules?.length ?? 0;

  return (
    <div className="main">
      <button className="btn btn-ghost btn-sm" style={{ marginBottom: 16, marginLeft: -8 }} onClick={() => navigate('/learn/browse')}>
        <Icon name="arrow-left" size={15} /> Back to catalogue
      </button>

      <div style={{ background: 'var(--indigo)', borderRadius: 16, padding: '40px 44px', marginBottom: 32, backgroundImage: 'radial-gradient(at 100% 0%, rgba(232,89,62,.3), transparent 55%)' }}>
        <div className="page-eyebrow" style={{ color: 'rgba(251,248,243,.6)', marginBottom: 8 }}>{course.category}</div>
        <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 40, fontWeight: 500, letterSpacing: '-0.02em', color: '#fbf8f3', margin: '0 0 12px', lineHeight: 1.08, maxWidth: '22ch' }}>{course.title}</h1>
        <p style={{ color: 'rgba(251,248,243,.75)', fontSize: 16, maxWidth: '52ch', margin: '0 0 24px', lineHeight: 1.6 }}>{course.description}</p>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 24 }}>
          <Avatar name={course.instructorName ?? 'Instructor'} size={28} />
          <span style={{ color: 'rgba(251,248,243,.8)', fontSize: 14 }}>by <strong style={{ color: '#fbf8f3' }}>{course.instructorName ?? 'Instructor'}</strong></span>
          <span style={{ color: 'rgba(251,248,243,.4)', fontSize: 14 }}>·</span>
          <span style={{ color: 'rgba(251,248,243,.7)', fontSize: 14 }}>{moduleCount} modules · {totalLessons} lessons</span>
          {isPrivate && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(251,248,243,.1)', color: '#fbf8f3', fontSize: 12, padding: '4px 10px', borderRadius: 999, fontWeight: 500 }}>
              <Icon name="lock" size={12} color="#fbf8f3" /> Private
            </span>
          )}
        </div>

        {isEnrolled && isStarted ? (
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <button className="btn btn-pulse" onClick={() => navigate(`/learn/courses/${id}/play`)}>
              {enrolment.progressPercent === 100 ? 'Review course' : 'Continue'} <Icon name="play" size={15} />
            </button>
            <ProgressBar value={enrolment.progressPercent ?? 0} height={6} color="#fbf8f3" />
            <span style={{ fontSize: 12, color: 'rgba(251,248,243,.6)', fontFamily: 'var(--font-mono)', whiteSpace: 'nowrap' }}>
              {enrolment.progressPercent ?? 0}% complete
            </span>
          </div>
        ) : isEnrolled && !isStarted ? (
          <button className="btn btn-pulse" onClick={handleStart} disabled={submitting}>
            {submitting ? 'Starting…' : 'Start Course'} <Icon name="arrow-right" size={15} />
          </button>
        ) : isPrivate ? (
          <button className="btn btn-pulse" onClick={() => setShowModal(true)}>
            Request access <Icon name="arrow-right" size={15} />
          </button>
        ) : (
          <button className="btn btn-pulse" onClick={handlePublicEnrol} disabled={submitting}>
            {submitting ? 'Enrolling…' : 'Enrol — free'} <Icon name="arrow-right" size={15} />
          </button>
        )}
      </div>

      <h2 className="section-head" style={{ marginTop: 0 }}>Course content</h2>
      <div style={{ background: '#fff', border: '1px solid var(--rule)', borderRadius: 12, overflow: 'hidden', marginBottom: 32 }}>
        {(course.modules ?? []).map(m => {
          const isOpen = openModules.has(m.id);
          return (
            <div key={m.id} className="module-block">
              <div className="module-header" style={{ cursor: 'pointer' }} onClick={() => toggleModule(m.id)}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <Icon name="chevron-down" size={15} color="var(--ink-3)" style={{ transform: isOpen ? 'rotate(0deg)' : 'rotate(-90deg)', transition: 'transform 0.2s' }} />
                  <span className="mod-title">{m.title}</span>
                </div>
                <span className="mod-count">{(m.lessons ?? []).length} lessons</span>
              </div>
              {isOpen && (
                <div>
                  {(m.lessons ?? []).map((l, idx) => (
                    <div key={l.lessonId} className="lesson-item" style={{ cursor: 'default' }}>
                      <div className="lesson-dot">{idx + 1}</div>
                      <span>{l.title}</span>
                      <span className="lesson-time" style={{ textTransform: 'capitalize', fontSize: 12 }}>
                        {l.contentType?.toLowerCase() ?? ''}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {showModal && (
        <Modal
          title="Request access"
          onClose={() => { setShowModal(false); setCodeError(''); setEnrollCode(''); }}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => { setShowModal(false); setCodeError(''); setEnrollCode(''); }}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={handleModalSubmit} disabled={submitting}>
                {submitting ? 'Sending…' : 'Send request'}
              </button>
            </>
          }
        >
          <p>Have an enrolment code? Enter it below for immediate access. Otherwise, send a request and the instructor will review it.</p>
          <div className="field">
            <label>Enrolment code (optional)</label>
            <input
              className="input"
              value={enrollCode}
              onChange={e => { setEnrollCode(e.target.value); setCodeError(''); }}
              placeholder="e.g. LEARN-2026-XY"
              style={{ fontFamily: 'var(--font-mono)' }}
            />
          </div>
          {codeError && (
            <div style={{ background: 'var(--danger-bg)', color: 'var(--danger)', borderRadius: 8, padding: '10px 14px', fontSize: 14, marginTop: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name="circle-alert" size={14} />
              {codeError}
            </div>
          )}
        </Modal>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
