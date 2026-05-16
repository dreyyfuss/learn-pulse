export default function KPICard({ label, value, delta, dir = 'up', variant = 'indigo' }) {
  return (
    <div className={`kpi-card kpi-card--${variant}`}>
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {delta && <div className={`kpi-delta ${dir}`}>{dir === 'up' ? '▲' : '▼'} {delta}</div>}
    </div>
  );
}