import { useState, useRef, useEffect } from 'react';
import courseService from '../services/courseService.js';
import Icon from './Icon.jsx';

const ACCEPT = {
  VIDEO:    'video/mp4,video/webm,video/ogg',
  DOCUMENT: 'application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/zip',
  ARTICLE:  null,
};

const ACCEPT_LABEL = {
  VIDEO:    'a video file (MP4, WebM, OGG)',
  DOCUMENT: 'a document (PDF, Word, ZIP)',
};

function isAllowedType(contentType, fileType) {
  const allowed = ACCEPT[contentType];
  if (!allowed) return true;
  return allowed.split(',').includes(fileType);
}

export default function LessonContentUpload({ courseId, moduleId, lessonId, contentType, hasContent, initialContent = '', onUploaded }) {
  const [status,       setStatus]       = useState('idle');   // idle | uploading | done | error
  const [progress,     setProgress]     = useState(0);
  const [error,        setError]        = useState('');
  const [markdown,     setMarkdown]     = useState('');
  const [fetching,     setFetching]     = useState(false);
  const [uploadedFile, setUploadedFile] = useState(null);
  const [objectUrl,    setObjectUrl]    = useState(null);
  const fileRef = useRef(null);

  useEffect(() => {
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [objectUrl]);

  useEffect(() => {
    if (contentType !== 'ARTICLE') return;
    if (!hasContent) {
      setMarkdown(initialContent);
      return;
    }
    setFetching(true);
    courseService.getLessonContent(courseId, moduleId, lessonId)
      .then(({ presignedUrl }) => fetch(presignedUrl).then(r => r.text()))
      .then(text => setMarkdown(text))
      .catch(() => setMarkdown(initialContent))
      .finally(() => setFetching(false));
  }, []);

  if (!contentType) return null;

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
    if (!isAllowedType(contentType, file.type)) {
      setError(`Wrong file type. Please upload ${ACCEPT_LABEL[contentType]}.`);
      e.target.value = '';
      return;
    }
    setError('');
    setUploadedFile(file);
    if (contentType === 'VIDEO') {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setObjectUrl(URL.createObjectURL(file));
    }
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
          {fetching ? (
            <p style={{ fontSize: 13, color: 'var(--ink-3)' }}>Loading content…</p>
          ) : (
            <textarea
              className="input textarea"
              rows={10}
              placeholder="Write your lesson content in Markdown…"
              value={markdown}
              onChange={e => setMarkdown(e.target.value)}
              disabled={status === 'uploading'}
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          )}
          <button
            className="btn btn-primary btn-sm"
            style={{ marginTop: 8 }}
            onClick={handleArticleUpload}
            disabled={fetching || status === 'uploading' || !markdown.trim()}
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

      {status === 'done' && uploadedFile && (
        <div style={{ marginTop: 12 }}>
          {contentType === 'VIDEO' && objectUrl && (
            <video
              controls
              src={objectUrl}
              style={{ width: '100%', borderRadius: 8, maxHeight: 240, background: '#000', display: 'block', marginBottom: 8 }}
            />
          )}
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: 'var(--success-bg, #f0fdf4)', border: '1px solid var(--success, #22c55e)', borderRadius: 8, padding: '6px 12px', fontSize: 13 }}>
            <Icon name="check-circle" size={14} color="var(--success, #22c55e)" />
            <span style={{ color: 'var(--ink-2)', fontWeight: 500 }}>{uploadedFile.name}</span>
            <span style={{ color: 'var(--ink-4)' }}>{(uploadedFile.size / 1024 / 1024).toFixed(1)} MB</span>
          </div>
        </div>
      )}
      {status === 'error' && <p style={{ color: 'var(--danger)', fontSize: 13, marginTop: 6 }}>{error}</p>}
    </div>
  );
}
