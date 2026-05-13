import { useState, useRef } from 'react';
import courseService from '../services/courseService.js';

const ACCEPT = {
  VIDEO:    'video/mp4,video/webm,video/ogg',
  DOCUMENT: 'application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/zip',
  ARTICLE:  null,
  OTHER:    null,
};

export default function LessonContentUpload({ courseId, moduleId, lessonId, contentType, hasContent, onUploaded }) {
  const [status,   setStatus]   = useState('idle');   // idle | uploading | done | error
  const [progress, setProgress] = useState(0);
  const [error,    setError]    = useState('');
  const [markdown, setMarkdown] = useState('');
  const fileRef = useRef(null);

  if (contentType === 'OTHER' || !contentType) return null;

  const uploadFile = async (file, mimeType) => {
    setStatus('uploading');
    setProgress(0);
    setError('');
    try {
      const { uploadUrl, objectKey } = await courseService.getContentUploadUrl(courseId, moduleId, lessonId, mimeType);

      await new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) setProgress(Math.round((e.loaded / e.total) * 100));
        };
        xhr.onload  = () => xhr.status < 300 ? resolve() : reject(new Error(`Upload failed: ${xhr.status}`));
        xhr.onerror = () => reject(new Error('Network error during upload'));
        xhr.open('PUT', uploadUrl);
        xhr.setRequestHeader('Content-Type', mimeType);
        xhr.send(file);
      });

      await courseService.confirmContentUpload(courseId, moduleId, lessonId, objectKey);
      setStatus('done');
      setProgress(100);
      onUploaded?.();
    } catch (err) {
      setStatus('error');
      setError(err.message || 'Upload failed');
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    uploadFile(file, file.type);
  };

  const handleArticleUpload = () => {
    if (!markdown.trim()) return;
    const blob = new Blob([markdown], { type: 'text/markdown' });
    uploadFile(blob, 'text/markdown');
  };

  return (
    <div style={{ marginTop: 8 }}>
      <label style={{ fontSize: 13, fontWeight: 500, color: 'var(--ink-2)', display: 'block', marginBottom: 6 }}>
        {hasContent ? 'Replace content' : 'Upload content'}
      </label>

      {contentType === 'ARTICLE' ? (
        <div>
          <textarea
            className="input textarea"
            rows={10}
            placeholder="Write your lesson content in Markdown…"
            value={markdown}
            onChange={e => setMarkdown(e.target.value)}
            disabled={status === 'uploading'}
            style={{ fontFamily: 'monospace', fontSize: 13 }}
          />
          <button
            className="btn btn-primary btn-sm"
            style={{ marginTop: 8 }}
            onClick={handleArticleUpload}
            disabled={status === 'uploading' || !markdown.trim()}
          >
            {status === 'uploading' ? `Uploading… ${progress}%` : 'Upload article'}
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <input
            ref={fileRef}
            type="file"
            accept={ACCEPT[contentType] ?? '*/*'}
            style={{ display: 'none' }}
            onChange={handleFileChange}
          />
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => fileRef.current?.click()}
            disabled={status === 'uploading'}
          >
            {status === 'uploading' ? `Uploading… ${progress}%` : 'Choose file'}
          </button>
          {status === 'uploading' && (
            <div style={{ flex: 1, height: 6, background: 'var(--rule)', borderRadius: 4, overflow: 'hidden' }}>
              <div style={{ width: `${progress}%`, height: '100%', background: 'var(--accent)', transition: 'width 0.2s' }} />
            </div>
          )}
        </div>
      )}

      {status === 'done'  && <p style={{ color: 'var(--success, green)',  fontSize: 13, marginTop: 6 }}>Uploaded successfully.</p>}
      {status === 'error' && <p style={{ color: 'var(--danger)',          fontSize: 13, marginTop: 6 }}>{error}</p>}
    </div>
  );
}
