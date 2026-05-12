import { useRouteError, isRouteErrorResponse, Link } from 'react-router-dom';

export default function ErrorPage() {
  const error = useRouteError();

  const status  = isRouteErrorResponse(error) ? error.status : null;
  const message = isRouteErrorResponse(error)
    ? error.statusText
    : (error?.message ?? 'An unexpected error occurred.');

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', minHeight: '100vh', gap: 12,
      fontFamily: 'inherit', color: 'var(--ink-1)',
    }}>
      <h1 style={{ fontSize: 48, fontWeight: 700, margin: 0 }}>
        {status ?? '✕'}
      </h1>
      <p style={{ color: 'var(--ink-3)', margin: 0 }}>{message}</p>
      <Link to="/" style={{ marginTop: 8 }}>Go home</Link>
    </div>
  );
}
