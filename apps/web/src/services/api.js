import useAuthStore from '../store/authStore';

const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

async function request(path, options = {}) {
  const { token } = useAuthStore.getState();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...options, headers });

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
