import Icon from '../../components/Icon';
import { CERTIFICATES } from '../../data/mockData';

export default function MyCertificates() {
  return (
    <div className="main">
      <div className="page-eyebrow">My certificates</div>
      <h1 className="page-title">Your certificates.</h1>
      <p className="page-lede">Each certificate is permanently linked. Download the PDF or share the link.</p>
      {CERTIFICATES.length === 0 ? (
        <div className="empty-state">
          <Icon name="award" size={40} color="var(--ink-4)" />
          <h3>No certificates yet.</h3>
          <p>Complete a course to earn your first certificate.</p>
        </div>
      ) : (
        <div className="cert-grid">
          {CERTIFICATES.map(cert => (
            <div key={cert.id} className="cert-card">
              <div className="cert-mark">✦</div>
              <div className="cert-course">{cert.courseTitle}</div>
              <div className="cert-meta">Completed {cert.completedDate} · by {cert.instructor}</div>
              <div className="cert-id">{cert.certId}</div>
              <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                <button className="btn btn-primary btn-sm"><Icon name="download" size={13} /> Download PDF</button>
                <button className="btn btn-secondary btn-sm"><Icon name="share-2" size={13} /> Share</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
