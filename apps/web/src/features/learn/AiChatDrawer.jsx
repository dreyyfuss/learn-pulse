import { useState, useRef, useEffect } from 'react';
import Icon from '../../components/Icon';

export default function AiChatDrawer({ courseName, onClose }) {
  const [messages, setMessages] = useState([
    { role: 'ai', text: 'Hi — I can help you with anything in this course. What are you working through?', sources: [] },
    { role: 'user', text: 'Can you explain why inserting at the head is O(1)?' },
    { role: 'ai', text: "When you insert at the head of a linked list, you only need to do two things: point the new node's `next` to the current head, then move the head pointer to the new node. That's exactly two operations — no matter how long the list is. That constant number of steps is what O(1) means.", sources: ['M02·L01 — Inserting at the head', 'M01·L01 — What is a pointer, really?'], showSources: false },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef();

  const send = () => {
    if (!input.trim() || loading) return;
    const userMsg = input.trim();
    setInput('');
    setMessages(m => [...m, { role: 'user', text: userMsg }]);
    setLoading(true);
    setTimeout(() => {
      setMessages(m => [...m, { role: 'ai', text: 'I\'m working through that — bear with me. (AI service not yet connected.)', sources: [], showSources: false }]);
      setLoading(false);
    }, 1000);
  };

  useEffect(() => {
    if (bottomRef.current) bottomRef.current.scrollTop = bottomRef.current.scrollHeight;
  }, [messages]);

  const toggleSources = (idx) => {
    setMessages(m => m.map((msg, i) => i === idx ? { ...msg, showSources: !msg.showSources } : msg));
  };

  return (
    <div className="ai-drawer">
      <div className="ai-header">
        <div style={{ width: 32, height: 32, borderRadius: 8, background: 'var(--indigo)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="sparkles" size={16} color="#fbf8f3" />
        </div>
        <div style={{ flex: 1 }}>
          <div className="ai-title">LearnPulse AI</div>
          <div className="ai-sub">{courseName}</div>
        </div>
        <button className="iconbtn" onClick={onClose}><Icon name="x" size={16} /></button>
      </div>
      <div className="ai-messages" ref={bottomRef}>
        {messages.map((msg, i) => (
          <div key={i}>
            <div className={`ai-bubble ${msg.role}`}>{msg.text}</div>
            {msg.role === 'ai' && msg.sources?.length > 0 && (
              <div className="ai-sources">
                <button className="ai-sources-toggle" onClick={() => toggleSources(i)}>
                  <Icon name={msg.showSources ? 'chevron-up' : 'chevron-down'} size={12} />
                  {msg.showSources ? 'Hide' : 'Show'} sources ({msg.sources.length})
                </button>
                {msg.showSources && msg.sources.map((s, j) => <div key={j} className="ai-source-item">{s}</div>)}
              </div>
            )}
          </div>
        ))}
        {loading && <div className="ai-bubble ai" style={{ color: 'var(--ink-3)', fontStyle: 'italic' }}>Thinking…</div>}
      </div>
      <div className="ai-input-bar">
        <textarea
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }}
          placeholder="Ask anything about this course…"
          rows={1}
        />
        <button className="btn btn-primary btn-sm" onClick={send} disabled={!input.trim() || loading}>
          <Icon name="send" size={14} />
        </button>
      </div>
    </div>
  );
}
