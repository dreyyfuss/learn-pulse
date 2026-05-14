import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const rate_limited = new Counter('rate_limited');
const credentials_rejected = new Counter('credentials_rejected');

export const options = {
  vus: 15,
  duration: '10s',
  thresholds: {
    // Must see at least one 429 — proves the limiter fired
    rate_limited: ['count>0'],
    // All requests should resolve fast (Traefik 429 is immediate)
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const res = http.post(
    'http://traefik/api/auth/login',
    JSON.stringify({ email: 'probe@learnpulse.test', password: 'probe' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status === 429) {
    rate_limited.add(1);
  } else if (res.status === 401) {
    credentials_rejected.add(1);
  }

  check(res, {
    'status is 429 or 401': (r) => r.status === 429 || r.status === 401,
    '429 has Retry-After header': (r) =>
      r.status !== 429 || r.headers['Retry-After'] !== undefined,
  });
}