export default function ProgressBar({ value = 0, height = 6, color }) {
  return (
    <div className="progress-bar" style={{ height }}>
      <div
        className={`progress-fill${value >= 100 ? ' complete' : ''}`}
        style={{ width: `${Math.min(100, value)}%`, ...(color ? { background: color } : {}) }}
      />
    </div>
  );
}
