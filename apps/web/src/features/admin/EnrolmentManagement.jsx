import { useState } from 'react';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Notification from '../../components/Notification';
import { ADMIN_USERS, ADMIN_COURSES } from '../../data/mockData';

const INIT_ENROLMENTS = [
  { id: 'e1', learner: ADMIN_USERS[0], course: ADMIN_COURSES[0], enrolled: '1 Apr 2026', status: 'in-progress' },
  { id: 'e2', learner: ADMIN_USERS[2], course: ADMIN_COURSES[0], enrolled: '15 Mar 2026', status: 'completed' },
  { id: 'e3', learner: ADMIN_USERS[3], course: ADMIN_COURSES[1], enrolled: '10 Feb 2026', status: 'in-progress' },
];

export default function EnrolmentManagement() {
  const [learnerSearch, setLearnerSearch] = useState('');
  const [courseSearch, setCourseSearch] = useState('');
  const [selectedLearner, setSelectedLearner] = useState(null);
  const [selectedCourse, setSelectedCourse] = useState(null);
  const [showLearnerDrop, setShowLearnerDrop] = useState(false);
  const [showCourseDrop, setShowCourseDrop] = useState(false);
  const [enrolments, setEnrolments] = useState(INIT_ENROLMENTS);
  const [toast, setToast] = useState('');

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

  const filteredLearners = ADMIN_USERS.filter(u => u.roles.includes('learner') && (!learnerSearch || u.name.toLowerCase().includes(learnerSearch.toLowerCase()) || u.email.toLowerCase().includes(learnerSearch.toLowerCase())));
  const filteredCourses = ADMIN_COURSES.filter(c => !courseSearch || c.title.toLowerCase().includes(courseSearch.toLowerCase()));

  const handleEnrol = () => {
    if (!selectedLearner || !selectedCourse) return;
    if (enrolments.find(e => e.learner.id === selectedLearner.id && e.course.id === selectedCourse.id)) { showToast('This learner is already enrolled.'); return; }
    setEnrolments(es => [...es, { id: `e${Date.now()}`, learner: selectedLearner, course: selectedCourse, enrolled: '6 May 2026', status: 'in-progress' }]);
    showToast(`${selectedLearner.name} enrolled in ${selectedCourse.title}.`);
    setSelectedLearner(null); setSelectedCourse(null); setLearnerSearch(''); setCourseSearch('');
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Admin · Enrolments</div>
      <h1 className="page-title">Enrolment management</h1>
      <p className="page-lede">Manually enrol or unenrol a learner from any course.</p>

      <div style={{ background: '#fff', border: '1px solid var(--rule)', borderRadius: 12, padding: '24px 28px', marginBottom: 32 }}>
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 500, margin: '0 0 18px' }}>Enrol a learner</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 14, alignItems: 'end' }}>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Learner</label>
            <div className="search-wrap" style={{ width: '100%' }}>
              <input className="input" placeholder="Search by name or email…"
                value={selectedLearner ? selectedLearner.name : learnerSearch}
                onChange={e => { setLearnerSearch(e.target.value); setSelectedLearner(null); setShowLearnerDrop(true); }}
                onFocus={() => setShowLearnerDrop(true)}
                onBlur={() => setTimeout(() => setShowLearnerDrop(false), 150)}
              />
              {showLearnerDrop && filteredLearners.length > 0 && (
                <div className="search-dropdown">
                  {filteredLearners.map(u => (
                    <div key={u.id} className="search-item" onMouseDown={() => { setSelectedLearner(u); setLearnerSearch(u.name); setShowLearnerDrop(false); }}>
                      <strong>{u.name}</strong> <span style={{ color: 'var(--ink-3)' }}>{u.email}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Course</label>
            <div className="search-wrap" style={{ width: '100%' }}>
              <input className="input" placeholder="Search courses…"
                value={selectedCourse ? selectedCourse.title : courseSearch}
                onChange={e => { setCourseSearch(e.target.value); setSelectedCourse(null); setShowCourseDrop(true); }}
                onFocus={() => setShowCourseDrop(true)}
                onBlur={() => setTimeout(() => setShowCourseDrop(false), 150)}
              />
              {showCourseDrop && filteredCourses.length > 0 && (
                <div className="search-dropdown">
                  {filteredCourses.map(c => (
                    <div key={c.id} className="search-item" onMouseDown={() => { setSelectedCourse(c); setCourseSearch(c.title); setShowCourseDrop(false); }}>
                      <strong>{c.title}</strong> <span style={{ color: 'var(--ink-3)' }}>{c.instructor}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <button className="btn btn-primary" disabled={!selectedLearner || !selectedCourse} onClick={handleEnrol}>
            <Icon name="user-plus" size={15} /> Enrol
          </button>
        </div>
      </div>

      <h2 className="section-head" style={{ marginTop: 0 }}>Recent enrolments</h2>
      <div className="table-wrap">
        <div className="table-row head" style={{ gridTemplateColumns: '1.2fr 1.8fr 100px 120px 80px' }}>
          <div>Learner</div><div>Course</div><div>Status</div><div>Enrolled</div><div>Action</div>
        </div>
        {enrolments.map(e => (
          <div key={e.id} className="table-row body" style={{ gridTemplateColumns: '1.2fr 1.8fr 100px 120px 80px' }}>
            <div>
              <div style={{ fontWeight: 500, fontSize: 14 }}>{e.learner.name}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{e.learner.email}</div>
            </div>
            <div style={{ fontSize: 14, color: 'var(--ink-2)' }}>{e.course.title}</div>
            <div><Tag variant={e.status === 'completed' ? 'completed' : 'in-progress'}>{e.status === 'completed' ? 'Completed' : 'In progress'}</Tag></div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{e.enrolled}</div>
            <div><button className="btn btn-danger btn-xs" onClick={() => { setEnrolments(es => es.filter(en => en.id !== e.id)); showToast('Learner unenrolled.'); }}>Unenrol</button></div>
          </div>
        ))}
      </div>
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
