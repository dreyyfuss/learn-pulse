import { useState, useEffect } from 'react';
import Icon from '../../components/Icon';
import Notification from '../../components/Notification';
import certificateService from '../../services/certificateService';

export default function MyCertificates() {
  const [certs, setCerts]       = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [copiedId, setCopiedId] = useState(null);
  const [toast, setToast]       = useState('');

  const showToast = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3500); };

  useEffect(() => {
    certificateService.listMine()
      .then(data => setCerts(data.data ?? data))
      .catch(() => setError('Could not load certificates.'))
      .finally(() => setLoading(false));
  }, []);

  const handleDownload = async (certUuid) => {
    try {
      await certificateService.downloadFile(certUuid);
    } catch {
      showToast('Download link unavailable. Please try again.');
    }
  };

  if (loading) return (
    <div className="main">
      <p style={{ color: 'var(--ink-3)' }}>Loading certificates…</p>
    </div>
  );

  if (error) return (
    <div className="main">
      <p style={{ color: 'var(--danger)' }}>{error}</p>
    </div>
  );

  return (
    <div className="main">
      <div className="page-eyebrow">My certificates</div>
      <h1 className="page-title">Your certificates.</h1>
      <p className="page-lede">Each certificate is permanently linked. Download the PDF or share the link.</p>

      {certs.length === 0 ? (
        <div className="empty-state">
          <Icon name="award" size={40} color="var(--ink-4)" />
          <h3>No certificates yet.</h3>
          <p>Complete a course to earn your first certificate.</p>
        </div>
      ) : (
        <div className="cert-grid">
          {certs.map(cert => (
            <div key={cert.certificateUuid} className="cert-card">
              <div className="cert-mark">✦</div>
              {cert.courseName && <div className="cert-course">{cert.courseName}</div>}
              {cert.learnerName && <div className="cert-learner">{cert.learnerName}</div>}
              <div className="cert-id">{cert.certificateUuid}</div>
              <div className="cert-meta">
                Issued {cert.issuedAt ? new Date(cert.issuedAt).toLocaleDateString() : '—'}
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                <button
                  className="btn btn-primary btn-sm"
                  onClick={() => handleDownload(cert.certificateUuid)}
                >
                  <Icon name="download" size={13} /> Download PDF
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(cert.certificateUuid);
                      setCopiedId(cert.certificateUuid);
                      setTimeout(() => setCopiedId(null), 2000);
                    } catch {
                      showToast('Could not copy — please select and copy the ID manually.');
                    }
                  }}
                >
                  <Icon name={copiedId === cert.certificateUuid ? 'check' : 'copy'} size={13} />
                  {copiedId === cert.certificateUuid ? 'Copied!' : 'Copy ID'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {toast && <Notification>{toast}</Notification>}
    </div>
  );
}