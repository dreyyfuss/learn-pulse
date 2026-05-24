import { useState, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import courseService from '../services/courseService.js';
import Icon from './Icon.jsx';

export default function LessonContentViewer({ courseId, moduleId, lessonId }) {
  const [content,  setContent]  = useState(null);   // { contentType, presignedUrl, fallbackUrl }
  const [markdown, setMarkdown] = useState('');
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState('');

  useEffect(() => {
    if (!lessonId || !moduleId) return;
    setLoading(true);
    setError('');
    setMarkdown('');

    courseService.getLessonContent(courseId, moduleId, lessonId)
      .then(res => {
        setContent(res);

        if (res.contentType === 'ARTICLE') {
          const url = res.presignedUrl ?? res.fallbackUrl;
          if (url) {
            return fetch(url).then(r => r.text()).then(setMarkdown);
          }
        }
      })
      .catch(err => setError(err.message || 'Could not load content.'))
      .finally(() => setLoading(false));
  }, [courseId, moduleId, lessonId]);

  if (loading) return <div style={{ color: 'var(--ink-3)', marginBottom: 16 }}>Loading content…</div>;
  if (error)   return <div style={{ color: 'var(--danger)',  marginBottom: 16 }}>{error}</div>;
  if (!content) return null;

  const url = content.presignedUrl ?? content.fallbackUrl;

  if (!url && !markdown) return (
    <div style={{ color: 'var(--ink-4)', marginBottom: 16, fontSize: 14 }}>
      No content uploaded yet.
    </div>
  );

  const type = content.contentType;

  return (
    <div style={{ marginBottom: 24 }}>
      {type === 'VIDEO' && url && (
        <video
          controls
          src={url}
          style={{ width: '100%', borderRadius: 12, maxHeight: 480, background: '#000' }}
        />
      )}

      {type === 'ARTICLE' && (
        <div className="lesson-reading">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{markdown || 'No content.'}</ReactMarkdown>
        </div>
      )}

      {type === 'DOCUMENT' && url && (
        <div>
          <iframe
            src={url}
            title="Document viewer"
            style={{ width: '100%', height: 600, border: 'none', borderRadius: 12 }}
          />
          <div style={{ marginTop: 10 }}>
            <a href={url} download className="btn btn-secondary btn-sm">
              <Icon name="download" size={14} /> Download
            </a>
          </div>
        </div>
      )}

      {(type === 'OTHER' || (!['VIDEO', 'ARTICLE', 'DOCUMENT'].includes(type))) && url && (
        <div>
          <a href={url} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm">
            <Icon name="file-text" size={14} /> Open resource
          </a>
        </div>
      )}
    </div>
  );
}
