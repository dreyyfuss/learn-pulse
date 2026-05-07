import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import { COURSES } from '../../data/mockData';

const BUILDER_MODULES = [
  { id: 'bm1', title: 'Module 1 — Big-O, calmly', lessons: [
    { id: 'bl1', title: 'What complexity even means' },
    { id: 'bl2', title: 'Counting steps on paper' },
    { id: 'bl3', title: 'The shapes of growth' },
  ]},
  { id: 'bm2', title: 'Module 2 — Searching', lessons: [
    { id: 'bl4', title: 'Linear search' },
    { id: 'bl5', title: 'Binary search, gently' },
    { id: 'bl6', title: 'Why sorted matters' },
  ]},
  { id: 'bm3', title: 'Module 3 — Sorting', lessons: [
    { id: 'bl7', title: "Bubble sort, and why we don't use it" },
    { id: 'bl8', title: 'Merge sort' },
  ]},
];

export default function CourseBuilder() {
  const { id } = useParams();
  const course = COURSES.find(c => c.id === id) || COURSES[3];
  const isLocked = course.status === 'locked';
  const [courseTitle, setCourseTitle] = useState(course.title);
  const [activeLesson, setActiveLesson] = useState('bl5');
  const [toast, setToast] = useState('');
  const [showPublish, setShowPublish] = useState(false);

  const save = () => { setToast('Draft saved.'); setTimeout(() => setToast(''), 2500); };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 60px)', width: '100%' }}>
      <div className="builder-topbar">
        {isLocked
          ? <span style={{ fontFamily: 'var(--font-display)', fontSize: 22, fontWeight: 500, color: 'var(--ink)', flex: 1 }}>{courseTitle}</span>
          : <input className="course-title-edit" value={courseTitle} onChange={e => setCourseTitle(e.target.value)} />
        }
        <Tag variant={course.status === 'published' ? 'published' : course.status === 'locked' ? 'locked' : 'draft'}>
          {course.status === 'published' ? 'Published' : course.status === 'locked' ? 'Locked' : 'Draft'}
        </Tag>
        {!isLocked && (
          <>
            <button className="btn btn-secondary btn-sm" onClick={save}>Save draft</button>
            <button className="btn btn-primary btn-sm" onClick={() => setShowPublish(true)}>Publish</button>
          </>
        )}
      </div>

      <div className="builder-shell" style={{ flex: 1, overflow: 'hidden' }}>
        <div className="builder-tree">
          <h4>Modules &amp; lessons</h4>
          {BUILDER_MODULES.map(m => (
            <div key={m.id}>
              <div className="tree-module active">{m.title}</div>
              {m.lessons.map(l => (
                <div key={l.id} className={`tree-lesson${activeLesson === l.id ? ' active' : ''}`} onClick={() => !isLocked && setActiveLesson(l.id)}>
                  {!isLocked && <span className="drag-handle"><Icon name="grip-vertical" size={13} /></span>}
                  {l.title}
                </div>
              ))}
            </div>
          ))}
          {!isLocked && (
            <button className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center', marginTop: 12 }}>
              <Icon name="plus" size={14} /> Add module
            </button>
          )}
        </div>

        <div className="builder-editor">
          {isLocked && (
            <div className="locked-banner">
              <Icon name="lock" size={16} />
              This course has active learners and can no longer be edited.
            </div>
          )}
          <div className="page-eyebrow" style={{ marginBottom: 4 }}>M02 · L02 · binary-search-gently</div>
          <h2 style={{ fontFamily: 'var(--font-display)', fontWeight: 500, fontSize: 24, letterSpacing: '-0.01em', margin: '0 0 24px' }}>Lesson editor</h2>
          <div className="field">
            <label>Lesson title</label>
            <input className="input" defaultValue="Binary search, gently" disabled={isLocked} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div className="field">
              <label>Content type</label>
              <select className="input select" disabled={isLocked}><option>Video</option><option>Reading</option><option>Exercise</option></select>
            </div>
            <div className="field">
              <label>Duration (minutes)</label>
              <input className="input" type="number" defaultValue="7" disabled={isLocked} />
            </div>
          </div>
          <div className="field">
            <label>Lesson description</label>
            <textarea className="input textarea" disabled={isLocked} defaultValue="Binary search is the answer to a single question: where in this sorted list could it be? Halve, halve, halve." />
          </div>
          <div className="field">
            <label>Content URL</label>
            <input className="input" defaultValue="lp://lessons/algorithms/m02-l02.video" disabled={isLocked} />
          </div>
          {!isLocked && (
            <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px solid var(--rule)', paddingTop: 18, marginTop: 8 }}>
              <button className="btn btn-danger btn-sm"><Icon name="trash-2" size={14} /> Delete lesson</button>
              <div style={{ display: 'flex', gap: 10 }}>
                <button className="btn btn-secondary btn-sm" onClick={save}>Save draft</button>
                <button className="btn btn-primary btn-sm">Save &amp; publish</button>
              </div>
            </div>
          )}
        </div>
      </div>

      {showPublish && (
        <Modal title="Publish course?" onClose={() => setShowPublish(false)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setShowPublish(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => { setShowPublish(false); setToast('Course published.'); setTimeout(() => setToast(''), 3000); }}>Publish</button>
            </>
          }
        >
          <p>Once published, learners can discover and enrol in <strong>{courseTitle}</strong>. You can still edit it until learners enrol.</p>
        </Modal>
      )}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
