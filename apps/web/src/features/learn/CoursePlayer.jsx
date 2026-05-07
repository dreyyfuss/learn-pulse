import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Icon from '../../components/Icon';
import ProgressBar from '../../components/ProgressBar';
import Notification from '../../components/Notification';
import AiChatDrawer from './AiChatDrawer';
import { MODULES_DATA } from '../../data/mockData';

export default function CoursePlayer() {
  const navigate = useNavigate();
  const [currentId, setCurrentId] = useState('l5');
  const [completed, setCompleted] = useState(new Set(['l1', 'l2', 'l3', 'l4']));
  const [showAI, setShowAI] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [toast, setToast] = useState('');

  const allLessons = MODULES_DATA.flatMap(m => m.lessons);
  const lesson = allLessons.find(l => l.id === currentId) || allLessons[0];
  const mod = MODULES_DATA.find(m => m.lessons.some(l => l.id === currentId));
  const isComplete = completed.has(currentId);
  const totalDone = completed.size;

  const markComplete = () => {
    setCompleted(s => new Set([...s, currentId]));
    setToast('Lesson complete.');
    setTimeout(() => setToast(''), 2500);
    const idx = allLessons.findIndex(l => l.id === currentId);
    const next = allLessons[idx + 1];
    if (next && !next.locked) setTimeout(() => setCurrentId(next.id), 800);
  };

  return (
    <div style={{ position: 'relative' }}>
      <div className="main" style={{ paddingBottom: 100 }}>
        <button className="btn btn-ghost btn-sm" style={{ marginBottom: 20, marginLeft: -8 }} onClick={() => navigate('/learn/courses/c1')}>
          <Icon name="arrow-left" size={15} /> Data structures, in plain English
        </button>

        <div className="player-shell">
          <div>
            {lesson.type === 'video' && (
              <div className="video-block">
                <div>
                  <div className="vid-mono">{mod?.id.toUpperCase()} · L0{lesson.idx} · {lesson.title.toLowerCase().replace(/ /g, '-')}</div>
                  <div className="vid-title">{lesson.title}</div>
                </div>
                <div className="vid-controls">
                  <button className="play-btn" onClick={() => setPlaying(!playing)}>
                    <Icon name={playing ? 'pause' : 'play'} size={18} color="#fbf8f3" />
                  </button>
                  <div className="scrub"><div className="scrub-fill" style={{ width: '38%' }} /></div>
                  <span className="vid-time">1:54 / 5:00</span>
                </div>
              </div>
            )}
            <div className="lesson-body">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <span className="lesson-content-type">
                  <Icon name={lesson.type === 'video' ? 'play-circle' : lesson.type === 'exercise' ? 'pencil' : 'file-text'} size={13} />
                  {lesson.type === 'video' ? 'Video · 5 min' : lesson.type === 'exercise' ? 'Exercise · 10 min' : 'Reading · 3 min'}
                </span>
              </div>
              <div className="lesson-breadcrumb">{mod?.title} / Lesson {lesson.idx}</div>
              <h2 className="lesson-title">{lesson.title}</h2>
              <div className="lesson-reading">
                <p>Inserting at the head of a linked list is the cheapest insert there is. You point your new node at the current head, then move the head pointer to the new node. That's it.</p>
                <p>In code: <code>node.next = head; head = node;</code> — two lines, constant time, no walking the list.</p>
                <p>Why does this matter? Because every other linked-list operation builds on this one. If you can draw it on paper without flinching, you can write it in any language.</p>
                <p>The key insight: you never need to know the length of the list, and you never touch any existing node except the old head. This is why it stays O(1) regardless of list size.</p>
              </div>
              <div className="attachments">
                <div style={{ fontSize: 12, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.06em', color: 'var(--ink-4)', marginBottom: 4 }}>Attachments</div>
                {['linked-list-diagrams.pdf', 'exercise-template.md'].map(f => (
                  <div key={f} className="attachment-item">
                    <Icon name="paperclip" size={14} />
                    <a href="#" onClick={e => e.preventDefault()}>{f}</a>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="lesson-sidebar">
            <div className="lesson-tree">
              <div className="lesson-tree-head">
                <div className="lt-eyebrow">Course progress</div>
                <div className="lt-progress">
                  <ProgressBar value={Math.round((totalDone / allLessons.length) * 100)} />
                  <span className="prog-label">{totalDone}/{allLessons.length}</span>
                </div>
              </div>
              {MODULES_DATA.map(m => (
                <div key={m.id} className="module-block">
                  <div className={`module-header${m.locked ? ' locked' : ''}`}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {m.locked ? <Icon name="lock" size={13} color="var(--ink-4)" /> : <Icon name="chevron-down" size={13} color="var(--ink-3)" />}
                      <span className="mod-title">{m.title}</span>
                    </div>
                    <span className="mod-count">{m.lessons.filter(l => completed.has(l.id)).length}/{m.lessons.length}</span>
                  </div>
                  {!m.locked && m.lessons.map((l, i) => (
                    <div
                      key={l.id}
                      className={`lesson-item${l.id === currentId ? ' active' : ''}${l.locked ? ' locked' : ''}`}
                      onClick={() => !l.locked && setCurrentId(l.id)}
                    >
                      <div className={`lesson-dot${completed.has(l.id) ? ' done' : l.id === currentId ? ' current' : ''}`}>
                        {completed.has(l.id) ? '✓' : i + 1}
                      </div>
                      <span style={{ lineHeight: 1.3 }}>{l.title}</span>
                      <span className="lesson-time">{l.mins}m</span>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="lesson-footer">
        <button className="btn btn-secondary btn-sm" onClick={() => {
          const idx = allLessons.findIndex(l => l.id === currentId);
          if (idx > 0) setCurrentId(allLessons[idx - 1].id);
        }}>
          <Icon name="chevron-left" size={15} /> Previous
        </button>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <button className={`btn btn-sm ${isComplete ? 'btn-secondary' : 'btn-pulse'}`} onClick={markComplete} disabled={isComplete}>
            {isComplete ? <><Icon name="check" size={14} /> Completed ✓</> : 'Mark as complete'}
          </button>
          <button className="btn btn-primary btn-sm" onClick={() => {
            const idx = allLessons.findIndex(l => l.id === currentId);
            const next = allLessons[idx + 1];
            if (next && !next.locked) setCurrentId(next.id);
          }}>
            Next <Icon name="chevron-right" size={15} />
          </button>
        </div>
      </div>

      <button className="ai-fab" style={{ right: showAI ? 440 : 24 }} onClick={() => setShowAI(!showAI)}>
        <Icon name="sparkles" size={15} /> Ask AI
      </button>

      {showAI && <AiChatDrawer courseName="Data structures, in plain English" onClose={() => setShowAI(false)} />}
      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}
