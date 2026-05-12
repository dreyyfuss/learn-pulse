import useAuthStore from '../store/authStore';
import api from './api';

const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

const aiService = {
  createSession(courseId) {
    return api.post(`/api/ai/courses/${courseId}/sessions`).then(d => d.sessionId);
  },

  async streamMessage(courseId, sessionId, message, { onToken, onDone, onError } = {}) {
    const { token } = useAuthStore.getState();
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    let resp;
    try {
      resp = await fetch(`${BASE}/api/ai/courses/${courseId}/sessions/${sessionId}/messages`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ message }),
      });
    } catch (err) {
      onError?.(err);
      return;
    }

    if (!resp.ok) {
      const err = new Error('AI request failed');
      err.status = resp.status;
      onError?.(err);
      return;
    }

    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop(); // keep incomplete trailing chunk

        for (const part of parts) {
          const trimmed = part.trim();
          if (!trimmed.startsWith('data:')) continue;
          const raw = trimmed.slice(5).trim();
          if (raw === '[DONE]') { onDone?.(); return; }
          try {
            const json = JSON.parse(raw);
            if (json.token) {
              onToken?.(json.token);
              // Yield to the browser between tokens so the paint queue
              // can flush — without this, all tokens in a single read()
              // chunk are processed synchronously and only one repaint occurs.
              await new Promise(r => setTimeout(r, 0));
            } else if (json.error) {
              onError?.(new Error(json.error));
            }
          } catch { /* skip malformed lines */ }
        }
      }
    } finally {
      reader.releaseLock();
    }

    onDone?.();
  },
};

export default aiService;
