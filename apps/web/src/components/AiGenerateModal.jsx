import { useState, useEffect, useRef } from 'react';
import Modal from './Modal';
import Icon from './Icon';
import courseService from '../services/courseService';
import { getErrorMessage } from '../utils/errorMessages';

const MIN_LEN = 10;
const MAX_LEN = 2000;
const POLL_INTERVAL_MS = 3000;
const TIMEOUT_S = 180;

const STATUS_MESSAGES = [
  'Designing course outline…',
  'Writing lesson content…',
  'Generating quizzes…',
];

export default function AiGenerateModal({ onClose, onSuccess }) {
  const [phase, setPhase]   = useState('prompt');   // 'prompt' | 'generating' | 'failed'
  const [prompt, setPrompt] = useState('');
  const [error, setError]   = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [msgIdx, setMsgIdx] = useState(0);

  const pollRef    = useRef(null);
  const elapsedRef = useRef(0);
  const msgTimerRef = useRef(null);

  // Cleanup on unmount
  useEffect(() => () => {
    clearInterval(pollRef.current);
    clearInterval(msgTimerRef.current);
  }, []);

  const stopPolling = () => {
    clearInterval(pollRef.current);
    clearInterval(msgTimerRef.current);
  };

  const startMessageCycle = () => {
    msgTimerRef.current = setInterval(() => {
      setMsgIdx(i => (i + 1) % STATUS_MESSAGES.length);
    }, 20_000);
  };

  const submit = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    setPhase('generating');
    elapsedRef.current = 0;
    setMsgIdx(0);
    startMessageCycle();

    try {
      const { jobId } = await courseService.generateCourse({ prompt: prompt.trim() });

      pollRef.current = setInterval(async () => {
        elapsedRef.current += POLL_INTERVAL_MS / 1000;

        if (elapsedRef.current > TIMEOUT_S) {
          stopPolling();
          setError(
            'This is taking longer than expected. Check "My Courses" later — your course will appear when ready.'
          );
          setPhase('failed');
          return;
        }

        try {
          const job = await courseService.getGenerationJob(jobId);
          if (job.status === 'COMPLETED') {
            stopPolling();
            onSuccess(job.courseId);
          } else if (job.status === 'FAILED') {
            stopPolling();
            setError(job.errorMessage || 'Generation failed. Please try again.');
            setPhase('failed');
          }
        } catch {
          // transient poll error — keep retrying
        }
      }, POLL_INTERVAL_MS);
    } catch (err) {
      stopPolling();
      setError(getErrorMessage(err));
      setPhase('failed');
      setSubmitting(false);
    }
  };

  const cancel = () => {
    stopPolling();
    onClose();
  };

  const retry = () => {
    setPhase('prompt');
    setError('');
    setSubmitting(false);
  };

  if (phase === 'generating') {
    return (
      <Modal
        title="Generate course with AI"
        onClose={null}
        actions={
          <button className="btn btn-secondary" onClick={cancel}>
            Cancel
          </button>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, padding: '24px 0' }}>
          <div style={{
            width: 40, height: 40, border: '3px solid var(--rule)',
            borderTopColor: 'var(--indigo)', borderRadius: '50%',
            animation: 'spin 0.8s linear infinite',
          }} />
          <div style={{ textAlign: 'center' }}>
            <p style={{ fontWeight: 600, fontSize: 15, margin: '0 0 4px' }}>Building your course…</p>
            <p style={{ color: 'var(--ink-3)', fontSize: 13, margin: 0 }}>This usually takes 1–2 minutes</p>
          </div>
          <p style={{ color: 'var(--ink-2)', fontSize: 13, margin: 0 }}>{STATUS_MESSAGES[msgIdx]}</p>
        </div>
      </Modal>
    );
  }

  if (phase === 'failed') {
    return (
      <Modal
        title="Generate course with AI"
        onClose={onClose}
        actions={
          <>
            <button className="btn btn-secondary" onClick={onClose}>Close</button>
            <button className="btn btn-primary" onClick={retry}>Try again</button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '8px 0' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--danger)' }}>
            <Icon name="alert-circle" size={18} />
            <span style={{ fontWeight: 600 }}>Generation failed</span>
          </div>
          <p style={{ color: 'var(--ink-2)', fontSize: 14, margin: 0 }}>{error}</p>
        </div>
      </Modal>
    );
  }

  // phase === 'prompt'
  const tooShort = prompt.trim().length < MIN_LEN;

  return (
    <Modal
      title="Generate course with AI"
      onClose={onClose}
      actions={
        <>
          <button className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button
            className="btn btn-primary"
            onClick={submit}
            disabled={tooShort}
          >
            <Icon name="sparkles" size={14} /> Generate
          </button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="field">
          <label>
            Describe the course you want to create{' '}
            <span style={{ color: 'var(--danger)' }}>*</span>
          </label>
          <textarea
            className="input textarea"
            rows={7}
            maxLength={MAX_LEN}
            placeholder={
              'e.g. Create a beginner course on REST API design for backend developers. ' +
              'Include HTTP methods, status codes, authentication, and best practices.'
            }
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            autoFocus
          />
          <p style={{ fontSize: 12, color: 'var(--ink-4)', margin: '4px 0 0', textAlign: 'right' }}>
            {prompt.length} / {MAX_LEN}
          </p>
        </div>

        <div style={{
          background: 'var(--paper-2)', borderRadius: 8, padding: '12px 14px',
          fontSize: 13, color: 'var(--ink-2)',
        }}>
          <p style={{ margin: '0 0 6px', fontWeight: 600, color: 'var(--ink-1)' }}>The AI will generate:</p>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 3 }}>
            <li>Course outline with 3–5 modules</li>
            <li>Full lesson content for each lesson</li>
            <li>A quiz per lesson</li>
          </ul>
        </div>

        {error && (
          <p style={{ color: 'var(--danger)', fontSize: 14, margin: 0 }}>{error}</p>
        )}
      </div>
    </Modal>
  );
}
