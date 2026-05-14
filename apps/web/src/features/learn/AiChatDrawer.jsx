import { useState, useRef, useEffect } from 'react';
import Icon from '../../components/Icon';
import aiService from '../../services/aiService';

export default function AiChatDrawer({ courseId, courseName, onClose }) {
  const [messages, setMessages] = useState([
    { role: 'ai', text: 'Hi — I can help you with anything in this course. What are you working through?' },
  ]);
  const [input, setInput]       = useState('');
  const [loading, setLoading]   = useState(false);
  const [sessionId, setSessionId] = useState(null);
  const bottomRef = useRef();

  useEffect(() => {
    if (bottomRef.current) bottomRef.current.scrollTop = bottomRef.current.scrollHeight;
  }, [messages, loading]);

  const send = async () => {
    if (!input.trim() || loading) return;
    const userMsg = input.trim();
    setInput('');
    setMessages(m => [...m, { role: 'user', text: userMsg }]);
    setLoading(true);

    let sid = sessionId;
    if (!sid) {
      try {
        sid = await aiService.createSession(courseId);
        setSessionId(sid);
      } catch {
        setMessages(m => [...m, { role: 'ai', text: 'Could not connect to the AI. Please try again.' }]);
        setLoading(false);
        return;
      }
    }

    let replyStarted = false;

    await aiService.streamMessage(courseId, sid, userMsg, {
      onToken(token) {
        if (!replyStarted) {
          replyStarted = true;
          setLoading(false);
          setMessages(m => [...m, { role: 'ai', text: token }]);
        } else {
          setMessages(m => {
            const copy = [...m];
            const last = copy[copy.length - 1];
            copy[copy.length - 1] = { ...last, text: last.text + token };
            return copy;
          });
        }
      },
      onDone() {
        setLoading(false);
      },
      onError() {
        setLoading(false);
        if (!replyStarted) {
          setMessages(m => [...m, { role: 'ai', text: 'Something went wrong. Please try again.' }]);
        }
      },
    });
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
          <div key={i} className={`ai-bubble ${msg.role}`}>{msg.text}</div>
        ))}
        {loading && (
          <div className="ai-bubble ai" style={{ color: 'var(--ink-3)', fontStyle: 'italic' }}>Thinking…</div>
        )}
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
