export default function Avatar({ name = 'User', size = 34 }) {
  const initials = name.split(' ').filter(Boolean).map(s => s[0]).slice(0, 2).join('').toUpperCase();
  return (
    <div style={{
      width: size, height: size, borderRadius: 999,
      background: 'var(--coral-100)', color: 'var(--coral-600)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontWeight: 700, fontSize: size * 0.38, fontFamily: 'var(--font-sans)',
      flexShrink: 0,
    }}>
      {initials}
    </div>
  );
}
