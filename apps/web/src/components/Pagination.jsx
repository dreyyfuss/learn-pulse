function getPageNumbers(page, totalPages) {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i);
  const pages = [0];
  if (page > 2) pages.push('...');
  for (let i = Math.max(1, page - 1); i <= Math.min(totalPages - 2, page + 1); i++) pages.push(i);
  if (page < totalPages - 3) pages.push('...');
  pages.push(totalPages - 1);
  return pages;
}

export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const pages = getPageNumbers(page, totalPages);
  return (
    <div className="pagination">
      <button className="page-btn" onClick={() => onChange(page - 1)} disabled={page === 0}>‹</button>
      {pages.map((p, i) =>
        p === '...'
          ? <span key={`e${i}`} className="page-ellipsis">…</span>
          : <button key={p} className={`page-btn${p === page ? ' active' : ''}`} onClick={() => onChange(p)}>{p + 1}</button>
      )}
      <button className="page-btn" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}>›</button>
    </div>
  );
}