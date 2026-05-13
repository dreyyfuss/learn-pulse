export function SkeletonLine({ width = '100%', height = 14, style }) {
  return <span className="skeleton" style={{ width, height, ...style }} />;
}

export function SkeletonCourseCard() {
  return (
    <div className="course-card" style={{ cursor: 'default', pointerEvents: 'none' }}>
      <span className="skeleton" style={{ height: 130, borderRadius: 8, display: 'block' }} />
      <SkeletonLine width="38%" height={11} />
      <SkeletonLine width="75%" height={18} />
      <SkeletonLine width="90%" height={13} />
      <SkeletonLine width="55%" height={13} />
      <div className="card-footer">
        <SkeletonLine width="100%" height={6} />
        <SkeletonLine width="44%" height={11} />
      </div>
    </div>
  );
}

export function SkeletonKPICard() {
  return (
    <div className="kpi-card">
      <SkeletonLine width="55%" height={11} style={{ marginBottom: 10 }} />
      <SkeletonLine width="45%" height={36} />
    </div>
  );
}

export function SkeletonTableRows({ cols, widths, count = 5 }) {
  return Array.from({ length: count }).map((_, i) => (
    <div key={i} className="table-row body" style={{ gridTemplateColumns: cols }}>
      {widths.map((w, j) => <SkeletonLine key={j} width={w} height={14} />)}
    </div>
  ));
}

export function SkeletonCertCard() {
  return (
    <div className="cert-card" style={{ pointerEvents: 'none' }}>
      <SkeletonLine width={12} height={18} />
      <SkeletonLine width="80%" height={18} style={{ marginTop: 8 }} />
      <SkeletonLine width="65%" height={13} />
      <SkeletonLine width="55%" height={13} />
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <SkeletonLine width={110} height={32} style={{ borderRadius: 8 }} />
        <SkeletonLine width={80}  height={32} style={{ borderRadius: 8 }} />
      </div>
    </div>
  );
}