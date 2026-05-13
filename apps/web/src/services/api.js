import useAuthStore from '../store/authStore';

const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

// Shared promise prevents concurrent 401s from each triggering their own refresh
let refreshing = null;

async function doTokenRefresh() {
  const { refreshToken, setAuth, clearAuth } = useAuthStore.getState();
  if (!refreshToken) { clearAuth(); throw new Error('No refresh token'); }

  const res = await fetch(`${BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) { clearAuth(); throw new Error('Refresh failed'); }

  const json = await res.json();
  const { accessToken, refreshToken: newRT, user } = json.data;
  const [firstName, ...rest] = (user?.fullName ?? '').trim().split(' ');
  setAuth({ ...user, firstName, lastName: rest.join(' ') }, accessToken, newRT ?? refreshToken);
  return accessToken;
}

async function request(path, options = {}) {
  const { token } = useAuthStore.getState();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...options, headers });

  if (res.status === 401) {
    try {
      if (!refreshing) refreshing = doTokenRefresh().finally(() => { refreshing = null; });
      const newToken = await refreshing;
      const retryRes = await fetch(`${BASE}${path}`, {
        ...options,
        headers: { ...headers, Authorization: `Bearer ${newToken}` },
      });
      if (!retryRes.ok) {
        let errBody;
        try { errBody = await retryRes.json(); } catch { errBody = { message: retryRes.statusText }; }
        const err = new Error(errBody.message ?? 'Request failed');
        err.status = retryRes.status;
        err.data = errBody;
        throw err;
      }
      if (retryRes.status === 204) return null;
      return retryRes.json();
    } catch {
      const err = new Error('Session expired. Please log in again.');
      err.status = 401;
      throw err;
    }
  }

  if (!res.ok) {
    let errBody;
    try { errBody = await res.json(); } catch { errBody = { message: res.statusText }; }
    const err = new Error(errBody.message ?? 'Request failed');
    err.status = res.status;
    err.data = errBody;
    throw err;
  }

  if (res.status === 204) return null;
  return res.json();
}

const api = {
  get: (path, opts) => request(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => request(path, { ...opts, method: 'POST', body: JSON.stringify(body) }),
  patch: (path, body, opts) => request(path, { ...opts, method: 'PATCH', body: JSON.stringify(body) }),
  put: (path, body, opts) => request(path, { ...opts, method: 'PUT', body: JSON.stringify(body) }),
  delete: (path, opts) => request(path, { ...opts, method: 'DELETE' }),
};

export default api;
