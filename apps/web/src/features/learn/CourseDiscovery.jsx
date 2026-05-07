import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import Tag from '../../components/Tag';
import Modal from '../../components/Modal';
import Notification from '../../components/Notification';
import { COURSES } from '../../data/mockData';

export default function CourseDiscovery() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('All');
  const [search, setSearch] = useState('');
  const [requestModal, setRequestModal] = useState(null);
  const [enrollCode, setEnrollCode] = useState('');
  const [toast, setToast] = useState('');

  const topics = ['All', 'Computer science', 'Design', 'Writing'];
  const filtered = COURSES.filter(c => {
    const matchTopic = filter === 'All' || c.topic === filter;
    const matchSearch = !search || c.title.toLowerCase().includes(search.toLowerCase()) || c.instructor.toLowerCase().includes(search.toLowerCase());
    return matchTopic && matchSearch;
  });

  const handleRequest = () => {
    setToast("Access requested. You'll hear back within 24 hours.");
    setRequestModal(null);
    setEnrollCode('');
    setTimeout(() => setToast(''), 3500);
  };

  return (
    <div className="main">
      <div className="page-eyebrow">Course catalogue</div>
      <h1 className="page-title">Find your next course.</h1>
      <p className="page-lede">Short, well-paced, made by people who've done the work.</p>

      <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid var(--rule)', borderRadius: 8, padding: '8px 14px', flex: 1, maxWidth: 360 }}>
          <Icon name="search" size={15} color="var(--ink-3)" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search courses, instructors…" style={{ border: 0, outline: 0, background: 'transparent', font: 'inherit', fontSize: 14, flex: 1, color: 'var(--ink)' }} />
        </div>
      </div>

      <div className="filter-row">
        {topics.map(t => (
          <span key={t} className={`chip${filter === t ? ' active' : ''}`} onClick={() => setFilter(t)}>{t}</span>
        ))}
      </div>

      <div className="course-grid">
        {filtered.map(c => (
          <div key={c.id} className="course-card" onClick={() => navigate(`/learn/courses/${c.id}`)}>
            <div className="thumb">
              <Icon name="book-open" size={32} className="thumb-icon" />
              {c.visibility === 'private' && (
                <span style={{ position: 'absolute', top: 10, right: 10, background: 'rgba(0,0,0,.5)', color: '#fff', fontSize: 11, padding: '3px 8px', borderRadius: 999, display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Icon name="lock" size={11} color="#fff" /> Private
                </span>
              )}
            </div>
            <div className="card-eyebrow">{c.topic} · {c.level}</div>
            <h3>{c.title}</h3>
            <p style={{ fontSize: 13, color: 'var(--ink-3)', margin: 0, lineHeight: 1.5 }}>{c.blurb}</p>
            <div className="card-meta">by {c.instructor} · {c.modules} modules · {c.lessons} lessons</div>
            <div className="card-footer">
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <Tag variant={c.visibility === 'private' ? 'private' : 'public'}>{c.visibility === 'private' ? 'Private' : 'Public'}</Tag>
                {c.progress > 0 && <Tag variant="enrolled">Enrolled</Tag>}
              </div>
              {c.progress === 0 && (
                <button
                  className={`btn btn-sm ${c.visibility === 'private' ? 'btn-secondary' : 'btn-primary'}`}
                  style={{ alignSelf: 'flex-start', marginTop: 8 }}
                  onClick={e => { e.stopPropagation(); c.visibility === 'private' ? setRequestModal(c) : navigate(`/learn/courses/${c.id}`); }}
                >
                  {c.visibility === 'private' ? 'Request access' : 'View course'}
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {requestModal && (
        <Modal title="Request access" onClose={() => setRequestModal(null)}
          actions={
            <>
              <button className="btn btn-secondary" onClick={() => setRequestModal(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleRequest}>Send request</button>
            </>
          }
        >
          <p>Have an enrolment code? Enter it below to get immediate access to <strong>{requestModal.title}</strong>. Otherwise, send a request and the instructor will review it.</p>
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
