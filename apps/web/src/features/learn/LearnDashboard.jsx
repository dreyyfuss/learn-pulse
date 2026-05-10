import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import ProgressBar from '../../components/ProgressBar';
import Tag from '../../components/Tag';
import useAuthStore from '../../store/authStore';
import enrolmentService from '../../services/enrolmentService';
import courseService from '../../services/courseService';

export default function LearnDashboard() {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const firstName = user?.firstName ?? 'there';

  const [enrolments, setEnrolments]             = useState([]);
  const [exploreCourses, setExploreCourses]     = useState([]);
  const [loading, setLoading]                   = useState(true);
  const [showStartModal, setShowStartModal]     = useState(false);
  const [pendingEnrolment, setPendingEnrolment] = useState(null);
  const [starting, setStarting]                 = useState(false);
  const [toast, setToast]                       = useState('');

  useEffect(() => {
    Promise.all([enrolmentService.listMine(), courseService.list({ size: 100 })])
      .then(([enrolData, courseData]) => {
        const items = enrolData.items ?? [];
        setEnrolments(items);
        const enrolledIds = new Set(items.map(e => e.courseId));
        const avail = (courseData.items ?? []).filter(
          c => c.status === 'PUBLISHED' && !enrolledIds.has(c.courseId ?? c.id)
        );
        setExploreCourses(avail);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const inProgress     = enrolments.filter(e => e.startedAt != null && e.status === 'ACTIVE');
  const notStarted     = enrolments.filter(e => e.startedAt == null && e.status === 'ACTIVE');
  const completed      = enrolments.filter(e => e.status === 'COMPLETED');
  const continueCourse = inProgress[0] ?? null;

  const openStartModal = (enrolment) => {
    setPendingEnrolment(enrolment);
    setShowStartModal(true);
  };

  const confirmStart = async () => {
    setStarting(true);
    try {
      await enrolmentService.startEnrolment(pendingEnrolment.enrolmentId);
      navigate(`/learn/courses/${pendingEnrolment.courseId}/play`);
    } catch (err) {
      setShowStartModal(false);
      setToast('Could not start: ' + err.message);
      setTimeout(() => setToast(''), 3000);
    } finally {
      setStarting(false);
      setPendingEnrolment(null);
    }
  };

  const today = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <div className="main">
      <div className="page-eyebrow">{today}</div>
      <h1 className="page-title">
        {continueCourse ? `Pick up where you left off, ${firstName}.` : `Welcome back, ${firstName}.`}
      </h1>
      <p className="page-lede">
        {continueCourse
          ? `Continue ${continueCourse.courseTitle}.`
          : 'Browse the catalogue to find your next course.'}
      </p>
      <div style={{ display: 'flex', gap: 10, marginBottom: 36 }}>
        {continueCourse ? (
          <button className="btn btn-primary" onClick={() => navigate(`/learn/courses/${continueCourse.courseId}/play`)}>
            Continue lesson <Icon name="play" size={15} />
          </button>
        ) : null}
        <button className="btn btn-secondary" onClick={() => navigate('/learn/browse')}>Browse catalogue</button>
      </div>

      {!loading && inProgress.length > 0 && (
        <>
          <h2 className="section-head">Continue learning</h2>
          <div className="course-grid">
            {inProgress.map(e => (
              <div key={e.enrolmentId} className="course-card" onClick={() => navigate(`/learn/courses/${e.courseId}/play`)}>
                <div className="thumb"><Icon name="book-open" size={32} className="thumb-icon" /></div>
                <div className="card-eyebrow">{e.courseTitle}</div>
                <h3>{e.courseTitle}</h3>
                <div className="card-footer">
                  <ProgressBar value={e.progressPercent ?? 0} />
                  <div className="card-stats">
                    <span>{e.progressPercent ?? 0}% complete</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {!loading && notStarted.length > 0 && (
        <>
          <h2 className="section-head">Enrolled — not started</h2>
          <div className="course-grid">
            {notStarted.map(e => (
              <div key={e.enrolmentId} className="course-card" onClick={() => navigate(`/learn/courses/${e.courseId}`)}>
                <div className="thumb"><Icon name="book-open" size={32} className="thumb-icon" /></div>
                <div className="card-eyebrow">{e.courseTitle}</div>
                <h3>{e.courseTitle}</h3>
                <div className="card-footer">
                  <button
                    className="btn btn-pulse btn-sm"
                    style={{ alignSelf: 'flex-start' }}
                    onClick={(ev) => { ev.stopPropagation(); openStartModal(e); }}
                  >
                    Start Course <Icon name="play" size={13} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {!loading && exploreCourses.length > 0 && (
        <>
          <h2 className="section-head">Explore courses</h2>
          <div className="course-grid">
            {exploreCourses.map(c => (
              <div key={c.courseId ?? c.id} className="course-card" onClick={() => navigate(`/learn/courses/${c.courseId ?? c.id}`)}>
                <div className="thumb"><Icon name="book-open" size={32} className="thumb-icon" /></div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="card-eyebrow">{c.category}</span>
                  {c.visibility === 'PRIVATE' && <Tag variant="private">Private 🔒</Tag>}
                </div>
                <h3>{c.title}</h3>
                <div className="card-footer">
                  <button className="btn btn-secondary btn-sm" style={{ alignSelf: 'flex-start' }}
                    onClick={(ev) => { ev.stopPropagation(); navigate(`/learn/courses/${c.courseId ?? c.id}`); }}>
                    {c.visibility === 'PRIVATE' ? 'Request access' : 'Enrol'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {!loading && completed.length > 0 && (
        <>
          <h2 className="section-head">Completed</h2>
          <div className="course-grid">
            {completed.map(e => (
              <div key={e.enrolmentId} className="course-card" onClick={() => navigate('/learn/certificates')}>
                <div className="thumb" style={{ backgroundImage: 'radial-gradient(at 100% 0%, rgba(47,122,77,.5), transparent 60%), radial-gradient(at 0% 100%, rgba(42,45,124,.6), transparent 60%)' }}>
                  <Icon name="award" size={32} className="thumb-icon" />
                </div>
                <div className="card-eyebrow">{e.courseTitle}</div>
                <h3>{e.courseTitle}</h3>
                <div className="card-footer">
                  <ProgressBar value={100} />
                  <div className="card-stats">
                    <span style={{ color: 'var(--success)' }}>Complete ✦</span>
                    <span>Completed</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {showStartModal && pendingEnrolment && (
        <Modal
          title="Start course?"
          onClose={() => { setShowStartModal(false); setPendingEnrolment(null); }}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => { setShowStartModal(false); setPendingEnrolment(null); }}>
                Cancel
              </button>
              <button className="btn btn-pulse" onClick={confirmStart} disabled={starting}>
                {starting ? 'Starting…' : 'Start course'}
              </button>
            </>
          }
        >
          <p>
            Starting <strong>{pendingEnrolment.courseTitle}</strong> will lock the course for editing. This cannot be undone. Continue?
          </p>
        </Modal>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
