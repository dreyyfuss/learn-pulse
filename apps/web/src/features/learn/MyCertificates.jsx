import { useState, useEffect } from 'react';
import Icon from '../../components/Icon';
import certificateService from '../../services/certificateService';

function formatDate(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

export default function MyCertificates() {
  const [certs, setCerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    certificateService
      .list()
      .then(data => setCerts(Array.isArray(data) ? data : []))
      .catch(err => setError(err.message ?? 'Failed to load certificates'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="main">
      <div className="page-eyebrow">My certificates</div>
      <h1 className="page-title">Your certificates.</h1>
      <p className="page-lede">Each certificate is permanently linked. Download the PDF or share the link.</p>

      {loading && (
        <div className="empty-state">
          <div className="spinner" />
          <p style={{ marginTop: 16 }}>Loading certificates...</p>
        </div>
      )}

      {!loading && error && (
        <div className="empty-state">
          <Icon name="alert-circle" size={40} color="var(--danger)" />
          <h3>Something went wrong.</h3>
          <p>{error}</p>
        </div>
      )}

      {!loading && !error && certs.length === 0 && (
        <div className="empty-state">
          <Icon name="award" size={40} color="var(--ink-4)" />
          <h3>No certificates yet.</h3>
          <p>Complete a course to earn your first certificate.</p>
        </div>
      )}

      {!loading && !error && certs.length > 0 && (
        <div className="cert-grid">
          {certs.map(cert => (
            <div key={cert.id} className="cert-card">
              <div className="cert-mark">*</div>
              <div className="cert-course">Course #{cert.courseId}</div>
              <div className="cert-meta">Issued {formatDate(cert.issuedAt)}</div>
              <div className="cert-id">{cert.id}</div>
              <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                <a
                  href={cert.downloadUrl}
                  className="btn btn-primary btn-sm"
                  style={{ textDecoration: 'none' }}
                >
                  <Icon name="download" size={13} /> Download PDF
                </a>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}