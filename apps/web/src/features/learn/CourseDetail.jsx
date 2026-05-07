import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import Avatar from '../../components/Avatar';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import ProgressBar from '../../components/ProgressBar';
import { COURSES, MODULES_DATA } from '../../data/mockData';

export default function CourseDetail() {
  const navigate = useNavigate();
  const { id } = useParams();
  const course = COURSES.find(c => c.id === id) || COURSES[0];
  const [expanded, setExpanded] = useState({ m1: true, m2: false, m3: false });
  const [showModal, setShowModal] = useState(false);
  const [enrollCode, setEnrollCode] = useState('');
  const [toast, setToast] = useState('');

  const enrolled = course.progress > 0;
  const toggle = (mid) => setExpanded(e => ({ ...e, [mid]: !e[mid] }));

  const handleEnrol = () => {
    if (course.visibility === 'private') { setShowModal(true); return; }
    setToast(`Enrolled in ${course.title}`);
    setTimeout(() => setToast(''), 3000);
    navigate('/learn/play');
  };

  return (
    <div className="main">
      <button className="btn btn-ghost btn-sm" style={{ marginBottom: 16, marginLeft: -8 }} onClick={() => navigate('/learn/browse')}>
        <Icon name="arrow-left" size={15} /> Back to catalogue
      </button>

      <div style={{ background: 'var(--indigo)', borderRadius: 16, padding: '40px 44px', marginBottom: 32, backgroundImage: 'radial-gradient(at 100% 0%, rgba(232,89,62,.3), transparent 55%)' }}>
        <div className="page-eyebrow" style={{ color: 'rgba(251,248,243,.6)', marginBottom: 8 }}>{course.topic} · {course.level}</div>
        <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 40, fontWeight: 500, letterSpacing: '-0.02em', color: '#fbf8f3', margin: '0 0 12px', lineHeight: 1.08, maxWidth: '22ch' }}>{course.title}</h1>
        <p style={{ color: 'rgba(251,248,243,.75)', fontSize: 16, maxWidth: '52ch', margin: '0 0 24px', lineHeight: 1.6 }}>{course.blurb}</p>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 24 }}>
          <Avatar name={course.instructor} size={28} />
          <span style={{ color: 'rgba(251,248,243,.8)', fontSize: 14 }}>by <strong style={{ color: '#fbf8f3' }}>{course.instructor}</strong></span>
          <span style={{ color: 'rgba(251,248,243,.4)', fontSize: 14 }}>·</span>
          <span style={{ color: 'rgba(251,248,243,.7)', fontSize: 14 }}>{course.modules} modules · {course.lessons} lessons</span>
          {course.visibility === 'private' && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(251,248,243,.1)', color: '#fbf8f3', fontSize: 12, padding: '4px 10px', borderRadius: 999, fontWeight: 500 }}>
              <Icon name="lock" size={12} color="#fbf8f3" /> Private
            </span>
          )}
        </div>
        {enrolled ? (
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <button className="btn btn-pulse" onClick={() => navigate('/learn/play')}>
              {course.progress === 100 ? 'Review course' : 'Continue'} <Icon name="play" size={15} />
            </button>
            <ProgressBar value={course.progress} height={6} color="#fbf8f3" />
            <span style={{ fontSize: 12, color: 'rgba(251,248,243,.6)', fontFamily: 'var(--font-mono)', whiteSpace: 'nowrap' }}>{course.lessonsDone}/{course.lessons} lessons</span>
          </div>
        ) : (
          <button className="btn btn-pulse" onClick={handleEnrol}>
            {course.visibility === 'private' ? 'Request access' : 'Enrol — free'} <Icon name="arrow-right" size={15} />
          </button>
        )}
      </div>

      <h2 className="section-head" style={{ marginTop: 0 }}>Course content</h2>
      <div style={{ background: '#fff', border: '1px solid var(--rule)', borderRadius: 12, overflow: 'hidden', marginBottom: 32 }}>
        {MODULES_DATA.map(m => (
          <div key={m.id} className="module-block">
            <div className={`module-header${m.locked ? ' locked' : ''}`} onClick={() => !m.locked && toggle(m.id)}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {m.locked ? <Icon name="lock" size={15} color="var(--ink-4)" /> : <Icon name={expanded[m.id] ? 'chevron-down' : 'chevron-right'} size={15} color="var(--ink-3)" />}
                <span className="mod-title">{m.title}</span>
                {m.locked && <Tag variant="locked">Locked</Tag>}
              </div>
              <span className="mod-count">{m.lessons.length} lessons · {m.lessons.reduce((a, l) => a + l.mins, 0)} min</span>
            </div>
            {expanded[m.id] && !m.locked && (
              <div>
                {m.lessons.map((l, idx) => (
                  <div key={l.id} className="lesson-item" onClick={() => navigate('/learn/play')}>
                    <div className={`lesson-dot${l.done ? ' done' : l.current ? ' current' : ''}`}>{l.done ? '✓' : idx + 1}</div>
                    <span>{l.title}</span>
                    <span className="lesson-time">{l.mins} min</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {showModal && (
        <Modal title="Request access" onClose={() => setShowModal(false)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => { setShowModal(false); setToast('Access requested.'); setTimeout(() => setToast(''), 3500); }}>Send request</button>
            </>
          }
        >
          <p>Have an enrolment code? Enter it below for immediate access. Otherwise, send a request and the instructor will review it.</p>
          <div className="field">
            <label>Enrolment code (optional)</label>
            <input className="input" value={enrollCode} onChange={e => setEnrollCode(e.target.value)} placeholder="e.g. LEARN-2026-XY" style={{ fontFamily: 'var(--font-mono)' }} />
          </div>
        </Modal>
      )}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
