export default function Tag({ variant = 'draft', children }) {
  return <span className={`tag tag-${variant}`}>{children}</span>;
}
